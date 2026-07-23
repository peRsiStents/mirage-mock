package com.miragemock.http;

import com.miragemock.common.util.JsonUtils;
import com.miragemock.core.engine.HttpMockResult;
import com.miragemock.core.engine.MockEngine;
import com.miragemock.core.engine.MockResponse;
import com.miragemock.core.log.RequestLogEntry;
import com.miragemock.core.log.RequestLogSink;
import com.miragemock.core.match.RequestSnapshot;
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
 */
@Component
public class MockHttpFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(MockHttpFilter.class);
    private static final int MAX_LOG_LEN = 8000;

    private final MockEngine engine;
    private final RequestLogSink logSink;
    private final MirageHttpProperties props;

    @Autowired
    public MockHttpFilter(MockEngine engine, RequestLogSink logSink, MirageHttpProperties props) {
        this.engine = engine;
        this.logSink = logSink;
        this.props = props;
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
            projectId = result.getProjectId();
            interfaceId = result.getInterfaceId();
            ruleId = result.getRuleId();
            matched = result.isMatched();
            responseRaw = writeResponse(resp, result.getResponse());
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
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "MOCK_INTERNAL_ERROR");
        body.put("message", t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
        String text = JsonUtils.toJson(body);
        resp.setStatus(500);
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
        InputStream is = req.getInputStream();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > MAX_LOG_LEN ? s.substring(0, MAX_LOG_LEN) + "...(truncated)" : s;
    }
}
