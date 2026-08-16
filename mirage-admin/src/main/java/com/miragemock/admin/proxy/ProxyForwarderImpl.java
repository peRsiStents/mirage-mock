package com.miragemock.admin.proxy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.miragemock.admin.mapper.RecordedResponseMapper;
import com.miragemock.common.entity.RecordedResponse;
import com.miragemock.common.util.JsonUtils;
import com.miragemock.core.engine.MockEngine;
import com.miragemock.core.match.RequestSnapshot;
import com.miragemock.http.proxy.MockProxy;
import com.miragemock.http.proxy.MockProxyResult;
import com.miragemock.http.proxy.RecordKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 录制回放代理实现（admin 侧装配）：
 * <ul>
 *   <li>回放：按请求签名（method|path|query|bodyHash）查录制快照，命中则返回并异步累加命中数；</li>
 *   <li>转发：将请求转发到接口配置的 upstream_url，录制模式/回放学习时异步落库快照（按签名 upsert）。</li>
 * </ul>
 * 转发失败返回 null，由 HTTP 层维持原有 404 行为。
 */
@Component
public class ProxyForwarderImpl implements MockProxy {

    private static final Logger log = LoggerFactory.getLogger(ProxyForwarderImpl.class);

    /** 转发上游时携带的请求头白名单 */
    private static final Set<String> FORWARD_REQUEST_HEADERS = new HashSet<>(Arrays.asList(
            "content-type", "authorization", "accept", "accept-language", "cookie", "user-agent"));

    /** 回写客户端的响应头白名单 */
    private static final Set<String> FORWARD_RESPONSE_HEADERS = new HashSet<>(Arrays.asList(
            "content-type", "content-disposition", "location", "set-cookie", "cache-control", "etag"));

    private final RestTemplate restTemplate;
    private final RecordedResponseMapper recordMapper;
    private final ExecutorService recordExecutor;

    public ProxyForwarderImpl(RestTemplate restTemplate, RecordedResponseMapper recordMapper) {
        this.restTemplate = restTemplate;
        this.recordMapper = recordMapper;
        this.recordExecutor = new ThreadPoolExecutor(
                1, 2, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(2000),
                new ThreadPoolExecutor.DiscardOldestPolicy());
    }

    @Override
    public MockProxyResult replay(MockEngine.ProxyHint hint, RequestSnapshot req) {
        String key = RecordKey.of(req.getMethod(), req.getPath(), req.getQueryRaw(), req.getBodyRaw());
        RecordedResponse rec = selectByKey(hint.getProjectId(), key);
        if (rec == null) {
            return null;
        }
        recordExecutor.execute(() -> {
            try {
                rec.setHitCount((rec.getHitCount() == null ? 0 : rec.getHitCount()) + 1);
                rec.setLastHitTime(LocalDateTime.now());
                recordMapper.updateById(rec);
            } catch (Exception e) {
                log.debug("回放命中计数更新失败", e);
            }
        });
        return toResult(rec);
    }

    @Override
    public MockProxyResult forward(MockEngine.ProxyHint hint, RequestSnapshot req) {
        String url = buildUrl(hint.getUpstreamUrl(), req);
        HttpHeaders headers = new HttpHeaders();
        if (req.getHeaders() != null) {
            for (Map.Entry<String, String> e : req.getHeaders().entrySet()) {
                String name = e.getKey();
                if (name.startsWith("x-") || FORWARD_REQUEST_HEADERS.contains(name)) {
                    headers.set(name, e.getValue());
                }
            }
        }
        byte[] bodyBytes = req.getBodyRaw() == null ? new byte[0] : req.getBodyRaw().getBytes(StandardCharsets.UTF_8);
        HttpEntity<byte[]> entity = new HttpEntity<>(bodyBytes, headers);
        try {
            HttpMethod method = HttpMethod.valueOf(req.getMethod() == null ? "GET" : req.getMethod().toUpperCase());
            ResponseEntity<byte[]> resp = restTemplate.exchange(url, method, entity, byte[].class);
            Map<String, String> respHeaders = new LinkedHashMap<>();
            for (String name : resp.getHeaders().keySet()) {
                if (name.toLowerCase().startsWith("x-") || FORWARD_RESPONSE_HEADERS.contains(name.toLowerCase())) {
                    respHeaders.put(name, resp.getHeaders().getFirst(name));
                }
            }
            byte[] respBody = resp.getBody() == null ? new byte[0] : resp.getBody();
            MockProxyResult result = new MockProxyResult(resp.getStatusCodeValue(), respHeaders,
                    new String(respBody, StandardCharsets.UTF_8));
            record(hint, req, result);
            return result;
        } catch (Exception e) {
            log.warn("上游转发失败 url={}: {}", url, e.getMessage());
            return null;
        }
    }

    // ============ 内部 ============

    private String buildUrl(String upstreamUrl, RequestSnapshot req) {
        String base = upstreamUrl.endsWith("/")
                ? upstreamUrl.substring(0, upstreamUrl.length() - 1) : upstreamUrl;
        String path = req.getPath() == null ? "" : req.getPath();
        StringBuilder sb = new StringBuilder(base).append(path);
        String query = req.getQueryRaw();
        if (query != null && !query.isEmpty() && path.indexOf('?') < 0) {
            sb.append('?').append(query);
        }
        return sb.toString();
    }

    private RecordedResponse selectByKey(Long projectId, String key) {
        return recordMapper.selectOne(new LambdaQueryWrapper<RecordedResponse>()
                .eq(RecordedResponse::getProjectId, projectId)
                .eq(RecordedResponse::getRecordKey, key)
                .last("LIMIT 1"));
    }

    private void record(MockEngine.ProxyHint hint, RequestSnapshot req, MockProxyResult result) {
        String key = RecordKey.of(req.getMethod(), req.getPath(), req.getQueryRaw(), req.getBodyRaw());
        recordExecutor.execute(() -> {
            try {
                RecordedResponse rec = selectByKey(hint.getProjectId(), key);
                if (rec == null) {
                    rec = new RecordedResponse();
                    rec.setProjectId(hint.getProjectId());
                    rec.setInterfaceId(hint.getInterfaceId());
                    rec.setMethod(req.getMethod());
                    rec.setPath(req.getPath());
                    rec.setRecordKey(key);
                    rec.setStatus(result.getStatus());
                    rec.setHeaders(JsonUtils.toJson(result.getHeaders()));
                    rec.setBody(result.getBody());
                    rec.setHitCount(0);
                    recordMapper.insert(rec);
                } else {
                    rec.setStatus(result.getStatus());
                    rec.setHeaders(JsonUtils.toJson(result.getHeaders()));
                    rec.setBody(result.getBody());
                    recordMapper.updateById(rec);
                }
            } catch (Exception e) {
                log.debug("录制快照写入失败", e);
            }
        });
    }

    private MockProxyResult toResult(RecordedResponse rec) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (rec.getHeaders() != null && !rec.getHeaders().isEmpty()) {
            try {
                Map<String, Object> parsed = JsonUtils.parseMap(rec.getHeaders());
                if (parsed != null) {
                    for (Map.Entry<String, Object> e : parsed.entrySet()) {
                        headers.put(e.getKey(), e.getValue() == null ? "" : e.getValue().toString());
                    }
                }
            } catch (Exception e) {
                log.debug("录制响应头解析失败", e);
            }
        }
        return new MockProxyResult(rec.getStatus() == null ? 200 : rec.getStatus(), headers, rec.getBody());
    }
}
