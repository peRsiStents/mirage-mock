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
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * HTTP Mock 拦截过滤器：仅当请求落在 Mock 端口时短路处理，其余透传给管理端。
 */
@Component
public class MockHttpFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(MockHttpFilter.class);

    /**
     * 请求日志中需要脱敏的敏感头（值替换为 ***）。
     * Mock 回放请求常携带真实 token，落库即成敏感数据汇聚点。
     */
    private static final Set<String> SENSITIVE_HEADERS = new HashSet<>(Arrays.asList(
            "authorization", "cookie", "set-cookie", "x-api-key", "x-auth-token",
            "proxy-authorization", "x-access-token", "x-token"));

    /**
     * 模板响应头黑名单：这些头由容器控制，模板里设置会破坏响应分帧。
     */
    private static final Set<String> FORBIDDEN_RESPONSE_HEADERS = new HashSet<>(Arrays.asList(
            "content-length", "transfer-encoding", "connection", "keep-alive", "upgrade"));

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

        // 端口判定必须用 getLocalPort()（真实接收端口）：
        // getServerPort() 取自请求 Host 头（客户端可控），会被伪造绕过端口隔离，
        // 反向代理场景下也会误判导致 Mock 功能整体失效。
        if (!props.isEnabled() || req.getLocalPort() != props.getPort()) {
            chain.doFilter(request, response);
            return;
        }

        long t0 = System.currentTimeMillis();

        // CORS 预检：浏览器跨端口「试跑」会先发 OPTIONS 探测
        if (props.isCorsEnabled() && "OPTIONS".equalsIgnoreCase(req.getMethod())) {
            applyCors(resp);
            resp.setStatus(204);
            resp.setContentLength(0);
            return;
        }

        Long projectId = null;
        Long interfaceId = null;
        Long ruleId = null;
        boolean matched = false;
        String requestRaw = "";
        String requestParsed = null;
        String responseRaw = "";
        boolean async = false;

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

            if (result.getResponse() != null && result.getResponse().getAction() == MockResponse.Action.TIMEOUT) {
                // TIMEOUT 故障：异步挂起，不占用容器工作线程
                async = doAsyncTimeout(req, resp, result.getResponse());
                responseRaw = "";
            } else {
                responseRaw = writeResponse(resp, result.getResponse());
            }
        } catch (BodyTooLargeException e) {
            log.warn("Mock 请求体超过 {} 字节上限: {} {}", props.getMaxBodyBytes(), req.getMethod(), req.getRequestURI());
            responseRaw = writeJsonError(resp, 413, "PAYLOAD_TOO_LARGE", "请求体超过 " + props.getMaxBodyBytes() + " 字节上限");
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
            // 异步模式下由异步线程负责写响应，filter 不得再提交响应
            if (!async) {
                try {
                    if (!resp.isCommitted()) {
                        resp.flushBuffer();
                    }
                } catch (Exception ignored) {
                    // 容器可能已提交，忽略
                }
            }
        }
    }

    @Override
    public void destroy() {
        // no-op
    }

    // ============ 响应写入 ============

    /**
     * TIMEOUT 故障注入：改用 servlet 异步，延迟期间不占用容器线程
     * （原本 Thread.sleep 会挂住工作线程，200 个并发请求即可打满线程池导致端口拒绝服务）。
     */
    private boolean doAsyncTimeout(HttpServletRequest req, HttpServletResponse resp, MockResponse mr)
            throws IOException {
        if (!req.isAsyncSupported()) {
            // 兜底：容器不支持异步时退化为同步等待（仅影响该次请求）
            log.warn("当前 Servlet 容器不支持异步，TIMEOUT 注入退化为同步等待");
            try {
                Thread.sleep(props.getTimeoutHangMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
        javax.servlet.AsyncContext async = req.startAsync();
        async.setTimeout(props.getTimeoutHangMs() + 5000L);
        long delay = props.getTimeoutHangMs();
        resp.setStatus(mr.getStatus() > 0 ? mr.getStatus() : 200);
        applyCors(resp);
        async.start(() -> {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            try {
                if (!resp.isCommitted()) {
                    resp.setContentType("application/json;charset=UTF-8");
                    resp.setCharacterEncoding("UTF-8");
                    resp.getWriter().write("");
                }
                async.complete();
            } catch (Exception e) {
                log.warn("TIMEOUT 异步响应写入失败", e);
                try {
                    async.complete();
                } catch (Exception ignored) {
                    // ignore
                }
            }
        });
        return true;
    }

    private String writeResponse(HttpServletResponse resp, MockResponse mr) throws IOException {
        if (mr == null) {
            return "";
        }
        if (mr.getAction() == MockResponse.Action.TIMEOUT) {
            // 正常路径下 TIMEOUT 走 doAsyncTimeout，这里仅兜底
            return "";
        }
        if (mr.getAction() == MockResponse.Action.RESET) {
            // 容器内无法真正发送 TCP RST：表现为「立即结束响应、连接关闭」，
            // 不写任何响应体。此处补一条 WARN，避免被误认为是真实 RST 语义。
            log.warn("RESET 故障注入：容器内表现为立即结束响应并关闭连接，非 TCP RST");
            applyCors(resp);
            return "";
        }
        resp.setStatus(mr.getStatus());
        if (mr.getHeaders() != null) {
            for (Map.Entry<String, String> e : mr.getHeaders().entrySet()) {
                if (e.getKey() == null) {
                    continue;
                }
                String name = e.getKey();
                if (FORBIDDEN_RESPONSE_HEADERS.contains(name.toLowerCase())) {
                    log.warn("模板响应头 {} 被忽略（由容器控制）", name);
                    continue;
                }
                resp.setHeader(name, e.getValue());
            }
        }
        String text;
        byte[] bytes;
        if (mr.getBody() == null) {
            text = "";
            bytes = new byte[0];
        } else if (mr.getBody() instanceof String) {
            text = (String) mr.getBody();
            bytes = text.getBytes(StandardCharsets.UTF_8);
            resp.setContentType("text/plain;charset=UTF-8");
        } else {
            text = JsonUtils.toJson(mr.getBody());
            bytes = text.getBytes(StandardCharsets.UTF_8);
            resp.setContentType("application/json;charset=UTF-8");
        }
        resp.setCharacterEncoding("UTF-8");
        applyCors(resp);
        // 显式设置 Content-Length：否则容器会用 chunked 传输，
        // 严格按 Content-Length 读体的客户端（嵌入式 SDK / 老网关）解析失败。
        resp.setContentLength(bytes.length);
        resp.getWriter().write(text);
        return text;
    }

    /** Mock 响应开放 CORS：管理端 UI 的「试跑」按浏览器同源策略跨端口直连 Mock 端口 */
    private void applyCors(HttpServletResponse resp) {
        if (!props.isCorsEnabled()) {
            return;
        }
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "*");
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
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        applyCors(resp);
        resp.setContentLength(bytes.length);
        resp.getWriter().write(text);
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
                .path(normalizePath(req.getRequestURI(), props.isTrailingSlashTolerant()))
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
            String v = req.getHeader(n);
            // 敏感头脱敏后再落库
            sb.append(n).append(": ").append(maskIfSensitive(n, v)).append('\n');
        }
        if (bodyRaw != null && !bodyRaw.isEmpty()) {
            sb.append('\n').append(bodyRaw);
        }
        return sb.toString();
    }

    /** 敏感头的值替换为 ***，保留键名便于排查 */
    private String maskIfSensitive(String headerName, String value) {
        if (headerName != null && SENSITIVE_HEADERS.contains(headerName.toLowerCase())) {
            return "***";
        }
        return value;
    }

    /**
     * 解析 query/form 参数。
     * 同名参数取**首个**（与 Servlet getParameter 语义一致），而不是最后一个。
     */
    private Map<String, String> parseKv(String kv) {
        Map<String, String> map = new HashMap<>();
        if (kv == null || kv.isEmpty()) {
            return map;
        }
        for (String pair : kv.split("&")) {
            int idx = pair.indexOf('=');
            try {
                String key = URLDecoder.decode(pair.substring(0, Math.max(idx, 0)), "UTF-8");
                String value = idx < 0 ? "" : URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                // 首个值优先：map.putIfAbsent 保证 ?a=1&a=2 取到 1
                map.putIfAbsent(key, value);
            } catch (UnsupportedEncodingException e) {
                map.putIfAbsent(pair, "");
            }
        }
        return map;
    }

    /**
     * 路径规范化：消除「偶发不命中」的常见来源。
     *
     * <p>依次处理：
     * <ol>
     *   <li>剥离路径参数（{@code ;jsessionid=xxx}）——容器/网关常自动追加</li>
     *   <li>逐段 URL 解码（畸形 % 序列保留原样）</li>
     *   <li>合并重复斜杠（{@code //api//users}）</li>
     *   <li>解析 {@code .} 与 {@code ..}（防止路径穿越式匹配）</li>
     *   <li>可选：容忍末尾斜杠</li>
     * </ol>
     */
    static String normalizePath(String rawUri, boolean trailingSlashTolerant) {
        if (rawUri == null || rawUri.isEmpty()) {
            return "/";
        }
        // 原始尾斜杠形态（剥离路径参数前记录，避免中间步骤污染判断）
        boolean trailingSlash = rawUri.endsWith("/");

        // 1) 剥离路径参数（每段第一个 ';' 之后的内容）
        StringBuilder stripped = new StringBuilder();
        for (String seg : rawUri.split("/", -1)) {
            int semi = seg.indexOf(';');
            stripped.append(semi >= 0 ? seg.substring(0, semi) : seg).append('/');
        }
        String path = stripped.toString();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        // 2) 逐段解码
        String[] segments = path.split("/", -1);
        for (int i = 0; i < segments.length; i++) {
            segments[i] = decodeSegment(segments[i]);
        }
        // 3)+4) 合并重复斜杠并解析 . / ..
        java.util.Deque<String> stack = new java.util.ArrayDeque<>();
        for (String seg : segments) {
            if (seg.isEmpty() || ".".equals(seg)) {
                continue;
            }
            if ("..".equals(seg)) {
                if (!stack.isEmpty()) {
                    stack.removeLast();
                }
                continue;
            }
            stack.addLast(seg);
        }
        StringBuilder out = new StringBuilder();
        for (String seg : stack) {
            out.append('/').append(seg);
        }
        String normalized = out.length() == 0 ? "/" : out.toString();
        // 末尾斜杠在此**保留**：尾斜杠容错由 MockEngine 双向处理
        // （pattern 无斜杠 / path 有斜杠，以及 pattern 有斜杠 / path 无斜杠），
        // 避免这里裁掉后 engine 无从得知原始形态。
        if (trailingSlash && normalized.length() > 1 && !normalized.endsWith("/")) {
            normalized = normalized + "/";
        }
        return normalized;
    }

    /** 单段 URL 解码；畸形 % 序列保留原样并记录 DEBUG */
    private static String decodeSegment(String seg) {
        if (seg.indexOf('%') < 0) {
            return seg;
        }
        try {
            return URLDecoder.decode(seg, "UTF-8");
        } catch (Exception e) {
            log.debug("路径段 URL 解码失败，保留原样: {}", seg);
            return seg;
        }
    }

    private byte[] readBody(HttpServletRequest req) throws IOException {
        InputStream is = req.getInputStream();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        long total = 0;
        while ((n = is.read(buf)) != -1) {
            total += n;
            if (total > props.getMaxBodyBytes()) {
                throw new BodyTooLargeException();
            }
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    private static final class BodyTooLargeException extends RuntimeException {
    }

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        int max = props.getLogTruncateLen();
        return s.length() > max ? s.substring(0, max) + "...(truncated)" : s;
    }
}
