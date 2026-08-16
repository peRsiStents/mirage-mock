package com.miragemock.http;

import com.miragemock.common.constant.Constants;
import com.miragemock.common.util.JsonUtils;
import com.miragemock.core.engine.HttpMockResult;
import com.miragemock.core.engine.MockEngine;
import com.miragemock.core.engine.MockResponse;
import com.miragemock.core.log.RequestLogEntry;
import com.miragemock.core.log.RequestLogSink;
import com.miragemock.core.match.RequestSnapshot;
import com.miragemock.http.proxy.MockProxy;
import com.miragemock.http.proxy.MockProxyResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP Mock 拦截过滤器：仅当请求落在 Mock 端口时短路处理，其余透传给管理端。
 * 规则未命中时支持录制回放（代理模式，见 {@link #tryProxy}）。
 */
@Component
public class MockHttpFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(MockHttpFilter.class);
    private static final int MAX_LOG_LEN = 8000;

    private final MockEngine engine;
    private final RequestLogSink logSink;
    private final MirageHttpProperties props;
    private final MockProxy mockProxy;

    @Autowired
    public MockHttpFilter(MockEngine engine, RequestLogSink logSink, MirageHttpProperties props,
                          MockProxy mockProxy) {
        this.engine = engine;
        this.logSink = logSink;
        this.props = props;
        this.mockProxy = mockProxy;
    }

    @Override
    public void init(FilterConfig filterConfig) {
        // no-op
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        if (!props.isEnabled() || req.getServerPort() != props.getPort()) {
            chain.doFilter(request, response);
            return;
        }

        long t0 = System.currentTimeMillis();
        Long projectId = null;
        Long interfaceId = null;
        Long ruleId = null;
        boolean matched = false;
        String requestRaw = "";
        String requestParsed = null;
        String responseRaw = "";

        try {
            String bodyRaw = new String(readBody(req), StandardCharsets.UTF_8);
            requestRaw = buildRequestRaw(req, bodyRaw);
            RequestSnapshot snapshot = buildSnapshot(req, bodyRaw);
            requestParsed = snapshot.getBody() == null ? null : JsonUtils.toJson(snapshot.getBody());

            HttpMockResult result = engine.handleHttp(snapshot);
            if (!result.isMatched()) {
                // 规则未命中 → 尝试录制回放（代理模式）
                MockProxyResult proxy = tryProxy(snapshot);
                if (proxy != null) {
                    MockEngine.ProxyHint hint = engine.resolveProxyHint(snapshot);
                    projectId = hint.getProjectId();
                    interfaceId = hint.getInterfaceId();
                    ruleId = null;
                    matched = true; // 由代理服务（录制/回放）给出响应
                    responseRaw = writeProxyResponse(resp, proxy);
                } else {
                    projectId = result.getProjectId();
                    interfaceId = result.getInterfaceId();
                    matched = false;
                    responseRaw = writeResponse(resp, result.getResponse());
                }
            } else {
                projectId = result.getProjectId();
                interfaceId = result.getInterfaceId();
                ruleId = result.getRuleId();
                matched = true;
                responseRaw = writeResponse(resp, result.getResponse());
            }
        } catch (BodyTooLargeException e) {
            log.warn("Mock 请求体超过 {} 字节上限: {} {}", props.getMaxBodyBytes(), req.getMethod(), req.getRequestURI());
            responseRaw = writeJsonError(resp, 413, "PAYLOAD_TOO_LARGE",
                    "请求体超过 " + props.getMaxBodyBytes() + " 字节上限");
        } catch (Throwable t) {
            log.error("Mock 处理异常: {} {}", req.getMethod(), req.getRequestURI(), t);
            responseRaw = writeError(resp, t);
        } finally {
            int cost = (int) (System.currentTimeMillis() - t0);
            try {
                logSink.append(RequestLogEntry.builder()
                        .projectId(projectId)
                        .interfaceId(interfaceId)
                        .ruleId(ruleId)
                        .protocol("HTTP")
                        .clientAddr(req.getRemoteAddr())
                        .requestRaw(truncate(requestRaw))
                        .requestParsed(truncate(requestParsed))
                        .responseRaw(truncate(responseRaw))
                        .matched(matched)
                        .costMs(cost)
                        .build());
            } catch (Exception e) {
                log.warn("请求日志写入失败", e);
            }
        }
    }

    @Override
    public void destroy() {
        // no-op
    }

    // ============ 录制回放（代理模式） ============

    /**
     * 规则未命中时的录制回放尝试：
     * 回放模式（2）先查录制快照；仍无结果且非关闭模式（1/2）且配置了上游地址时转发上游（并按需录制）。
     *
     * @return 代理响应；未启用/无快照/转发失败返回 null（保持原有 404 行为）
     */
    private MockProxyResult tryProxy(RequestSnapshot snapshot) {
        MockEngine.ProxyHint hint = engine.resolveProxyHint(snapshot);
        if (hint == null || hint.getUpstreamUrl() == null || hint.getUpstreamUrl().isEmpty()) {
            return null;
        }
        MockProxyResult proxy = null;
        if (hint.getRecordMode() == Constants.RECORD_MODE_REPLAY) {
            proxy = mockProxy.replay(hint, snapshot);
        }
        if (proxy == null && hint.getRecordMode() != Constants.RECORD_MODE_OFF) {
            proxy = mockProxy.forward(hint, snapshot);
        }
        return proxy;
    }

    private String writeProxyResponse(HttpServletResponse resp, MockProxyResult proxy) throws IOException {
        resp.setStatus(proxy.getStatus());
        for (Map.Entry<String, String> e : proxy.getHeaders().entrySet()) {
            resp.setHeader(e.getKey(), e.getValue());
        }
        String text = proxy.getBody();
        resp.setCharacterEncoding("UTF-8");
        if (text == null || text.isEmpty()) {
            return "";
        }
        resp.setContentType(proxy.getHeaders().getOrDefault("Content-Type", "application/json;charset=UTF-8"));
        resp.getWriter().write(text);
        resp.getWriter().flush();
        return text;
    }

    // ============ 响应写入 ============

    private String writeResponse(HttpServletResponse resp, MockResponse mr) throws IOException {
        if (mr.getAction() == MockResponse.Action.TIMEOUT) {
            try {
                Thread.sleep(props.getTimeoutHangMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "";
        }
        if (mr.getAction() == MockResponse.Action.RESET) {
            // 不写任何响应，连接由容器关闭
            return "";
        }
        resp.setStatus(mr.getStatus());
        if (mr.getHeaders() != null) {
            for (Map.Entry<String, String> e : mr.getHeaders().entrySet()) {
                resp.setHeader(e.getKey(), e.getValue());
            }
        }
        String text;
        if (mr.getBody() == null) {
            text = "";
        } else if (mr.getBody() instanceof String) {
            text = (String) mr.getBody();
            resp.setContentType("text/plain;charset=UTF-8");
        } else {
            text = JsonUtils.toJson(mr.getBody());
            resp.setContentType("application/json;charset=UTF-8");
        }
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write(text);
        resp.getWriter().flush();
        return text;
    }

    private String writeError(HttpServletResponse resp, Throwable t) throws IOException {
        // 不回 t.getMessage()（可能含内部细节），固定文案；详情只在服务端日志
        return writeJsonError(resp, 500, "MOCK_INTERNAL_ERROR", "Mock 处理异常");
    }

    private String writeJsonError(HttpServletResponse resp, int status, String error, String message) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", error);
        body.put("message", message);
        String text = JsonUtils.toJson(body);
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write(text);
        resp.getWriter().flush();
        return text;
    }

    // ============ 请求快照构建 ============

    private RequestSnapshot buildSnapshot(HttpServletRequest req, String bodyRaw) {
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> names = req.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name.toLowerCase(), req.getHeader(name));
        }
        String contentType = req.getContentType();
        Map<String, String> query = parseKv(req.getQueryString());
        Map<String, String> form = (contentType != null && contentType.contains("application/x-www-form-urlencoded"))
                ? parseKv(bodyRaw) : new HashMap<>();
        Map<String, Object> body = null;
        if (contentType != null && contentType.contains("application/json") && bodyRaw != null && !bodyRaw.isEmpty()) {
            try {
                body = JsonUtils.parseMap(bodyRaw);
            } catch (Exception e) {
                body = null;
            }
        }
        return RequestSnapshot.builder()
                .protocol("HTTP")
                .method(req.getMethod())
                .path(req.getRequestURI())
                .headers(headers)
                .query(query)
                .queryRaw(req.getQueryString())
                .form(form)
                .bodyRaw(bodyRaw)
                .body(body)
                .fields(body)
                .clientAddr(req.getRemoteAddr())
                .build();
    }

    private String buildRequestRaw(HttpServletRequest req, String bodyRaw) {
        StringBuilder sb = new StringBuilder();
        sb.append(req.getMethod()).append(' ').append(req.getRequestURI());
        if (req.getQueryString() != null) {
            sb.append('?').append(req.getQueryString());
        }
        sb.append('\n');
        Enumeration<String> names = req.getHeaderNames();
        while (names.hasMoreElements()) {
            String n = names.nextElement();
            sb.append(n).append(": ").append(req.getHeader(n)).append('\n');
        }
        if (bodyRaw != null && !bodyRaw.isEmpty()) {
            sb.append('\n').append(bodyRaw);
        }
        return sb.toString();
    }

    private Map<String, String> parseKv(String kv) {
        Map<String, String> map = new HashMap<>();
        if (kv == null || kv.isEmpty()) {
            return map;
        }
        for (String pair : kv.split("&")) {
            int idx = pair.indexOf('=');
            try {
                if (idx < 0) {
                    map.put(URLDecoder.decode(pair, "UTF-8"), "");
                } else {
                    map.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"),
                            URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
                }
            } catch (UnsupportedEncodingException e) {
                map.put(pair, "");
            }
        }
        return map;
    }

    private byte[] readBody(HttpServletRequest req) throws IOException {
        int max = props.getMaxBodyBytes();
        String cl = req.getHeader("Content-Length");
        if (cl != null) {
            try {
                if (Long.parseLong(cl.trim()) > max) {
                    throw new BodyTooLargeException();
                }
            } catch (NumberFormatException ignore) {
                // 非法 Content-Length 忽略，按实际读取大小判定
            }
        }
        InputStream is = req.getInputStream();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        long total = 0;
        while ((n = is.read(buf)) != -1) {
            total += n;
            if (total > max) {
                throw new BodyTooLargeException();
            }
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    /** Mock 请求体超限标记（上限见 mirage.http.max-body-bytes，默认 1MB） */
    private static final class BodyTooLargeException extends RuntimeException {
    }

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > MAX_LOG_LEN ? s.substring(0, MAX_LOG_LEN) + "...(truncated)" : s;
    }
}
