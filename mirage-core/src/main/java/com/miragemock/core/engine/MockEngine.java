package com.miragemock.core.engine;

import com.miragemock.common.api.ResultCode;
import com.miragemock.common.constant.Constants;
import com.miragemock.common.entity.MockRule;
import com.miragemock.common.exception.BizException;
import com.miragemock.common.util.JsonUtils;
import com.miragemock.core.cache.CompiledInterface;
import com.miragemock.core.cache.CompiledRule;
import com.miragemock.core.cache.ProjectSnapshot;
import com.miragemock.core.cache.RuleCache;
import com.miragemock.core.match.MatchConditionEvaluator;
import com.miragemock.core.match.RequestSnapshot;
import com.miragemock.core.render.RenderedResponse;
import com.miragemock.core.render.TemplateRenderer;
import com.miragemock.dsl.eval.EvalContext;
import com.miragemock.dsl.spi.SecretResolver;
import com.miragemock.dsl.spi.SeqProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mock 运行时内核：项目解析 → 接口路由 → 规则匹配 → 延迟/故障注入 → 模板渲染。
 */
@Component
public class MockEngine {

    private static final Logger log = LoggerFactory.getLogger(MockEngine.class);
    private static final int MAX_DELAY_MS = 60_000;

    private final RuleCache cache;
    private final MatchConditionEvaluator matcher;
    private final TemplateRenderer renderer;
    private final SecretResolver secretResolver;
    private final SeqProvider seqProvider;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Autowired
    public MockEngine(RuleCache cache, MatchConditionEvaluator matcher, TemplateRenderer renderer,
                      SecretResolver secretResolver, SeqProvider seqProvider) {
        this.cache = cache;
        this.matcher = matcher;
        this.renderer = renderer;
        this.secretResolver = secretResolver;
        this.seqProvider = seqProvider;
    }

    public HttpMockResult handleHttp(RequestSnapshot req) {
        String code = req.getHeaders() == null ? null : req.getHeaders().get(Constants.HEADER_PROJECT_CODE.toLowerCase());
        boolean hasProjectHeader = code != null && !code.isEmpty();

        ProjectSnapshot snap = resolveProject(req);
        InterfaceMatch im;
        if (snap != null) {
            // 有项目标识（header 指定 或 全局仅一个项目）：在该项目内路由
            im = matchInterface(snap, req);
        } else if (!hasProjectHeader) {
            // 无标识且多项目：跨项目按 method+path 唯一定位（路径全局唯一即可命中）
            im = resolveByUniquePath(req);
        } else {
            // header 指定的项目编码不存在
            im = null;
        }
        if (im == null) {
            Long pid = snap == null ? null : snap.getProjectId();
            return HttpMockResult.notMatched(pid, null, noRuleResponse(req));
        }
        snap = im.snapshot;

        CompiledRule hit = null;
        for (CompiledRule rule : im.iface.getRules()) {
            if (matcher.match(rule, req, im.pathVars)) {
                hit = rule;
                break;
            }
        }
        if (hit == null) {
            return HttpMockResult.notMatched(snap.getProjectId(), im.iface.getId(), noRuleResponse(req));
        }

        MockResponse response = applyFaultOrRender(hit, req, im.pathVars, snap);
        return new HttpMockResult(snap.getProjectId(), im.iface.getId(), hit.getId(), true, response);
    }

    // ============ 项目解析 ============

    private ProjectSnapshot resolveProject(RequestSnapshot req) {
        String code = null;
        if (req.getHeaders() != null) {
            code = req.getHeaders().get(Constants.HEADER_PROJECT_CODE.toLowerCase());
        }
        if (code != null && !code.isEmpty()) {
            return cache.getByCode(code);
        }
        return cache.singleProjectOrDefault();
    }

    // ============ 接口路由 ============

    private InterfaceMatch matchInterface(ProjectSnapshot snap, RequestSnapshot req) {
        String method = req.getMethod() == null ? "" : req.getMethod().toUpperCase();
        for (CompiledInterface iface : snap.httpInterfaces()) {
            Map<String, String> vars = matchInterfacePattern(iface, method, req.getPath());
            if (vars != null) {
                return new InterfaceMatch(snap, iface, vars);
            }
        }
        return null;
    }

    /**
     * 无项目标识且存在多项目时：在所有项目里按 method+path 查找接口。
     * 全局唯一匹配则返回；零匹配、或多项目均存在该路径（歧义）时返回 null（此时需显式带项目 header）。
     */
    private InterfaceMatch resolveByUniquePath(RequestSnapshot req) {
        String method = req.getMethod() == null ? "" : req.getMethod().toUpperCase();
        InterfaceMatch found = null;
        for (ProjectSnapshot snap : cache.all()) {
            for (CompiledInterface iface : snap.httpInterfaces()) {
                Map<String, String> vars = matchInterfacePattern(iface, method, req.getPath());
                if (vars == null) {
                    continue;
                }
                if (found != null) {
                    // 多个项目都匹配到该 method+path → 歧义，放弃（要求显式 header）
                    return null;
                }
                found = new InterfaceMatch(snap, iface, vars);
            }
        }
        return found;
    }

    /** method+path 是否匹配该接口；匹配则返回路径变量，否则 null（method 空或 ANY 视为通配） */
    private Map<String, String> matchInterfacePattern(CompiledInterface iface, String method, String path) {
        String m = iface.getEntity().getHttpMethod();
        boolean methodOk = m == null || m.isEmpty() || "ANY".equalsIgnoreCase(m) || m.equalsIgnoreCase(method);
        if (!methodOk) {
            return null;
        }
        String pattern = iface.getEntity().getHttpPath();
        if (pattern == null) {
            return null;
        }
        if (pathMatcher.match(pattern, path)) {
            return pathMatcher.extractUriTemplateVariables(pattern, path);
        }
        // 尾斜杠容忍（双向）：
        //   path 有尾斜杠、pattern 无 → /api/orders/ 命中 /api/orders
        //   path 无尾斜杠、pattern 有 → /api/orders 命中 /api/orders/
        if (path.length() > 1 && path.endsWith("/")) {
            String trimmed = path.substring(0, path.length() - 1);
            if (pathMatcher.match(pattern, trimmed)) {
                return pathMatcher.extractUriTemplateVariables(pattern, trimmed);
            }
        } else if (pattern.length() > 1 && pattern.endsWith("/")) {
            String trimmedPattern = pattern.substring(0, pattern.length() - 1);
            if (pathMatcher.match(trimmedPattern, path)) {
                return pathMatcher.extractUriTemplateVariables(trimmedPattern, path);
            }
        }
        return null;
    }

    // ============ 延迟 / 故障注入 / 渲染 ============

    private MockResponse applyFaultOrRender(CompiledRule rule, RequestSnapshot req,
                                             Map<String, String> pathVars, ProjectSnapshot snap) {
        applyDelay(rule.getEntity());
        String fault = rule.getEntity().getFaultType();
        if ("RESET".equalsIgnoreCase(fault)) {
            return MockResponse.reset();
        }
        if ("TIMEOUT".equalsIgnoreCase(fault)) {
            return MockResponse.timeout();
        }
        if ("ERROR_STATUS".equalsIgnoreCase(fault)) {
            return errorStatusResponse(rule.getEntity());
        }
        // 正常渲染
        Map<String, Object> baseVars = new HashMap<>();
        if (pathVars != null) {
            for (Map.Entry<String, String> e : pathVars.entrySet()) {
                baseVars.put("path." + e.getKey(), e.getValue());
            }
        }
        // 请求体展平为 field.*，供响应模板回显，如 ${field.body.aac001}
        flattenRequestFields(req, baseVars);
        EvalContext ctx = new EvalContext(baseVars, secretResolver, seqProvider, snap.getProjectId());
        RenderedResponse rr = renderer.render(rule.getTemplateNode(), baseVars, ctx);
        return MockResponse.write(rr.getStatus(), rr.getHeaders(), rr.getBody());
    }

    /** 将请求体 JSON 对象展平为 field.* 变量（与 TCP 一致），便于响应模板引用请求字段。 */
    @SuppressWarnings("unchecked")
    private void flattenRequestFields(RequestSnapshot req, Map<String, Object> out) {
        String bodyRaw = req.getBodyRaw();
        if (bodyRaw == null || bodyRaw.isEmpty()) {
            return;
        }
        try {
            Object parsed = JsonUtils.parseMap(bodyRaw);
            if (parsed instanceof Map) {
                flattenFields("field", (Map<String, Object>) parsed, out);
            }
        } catch (Exception ignore) {
            // 非 JSON 对象体（数组/纯文本等）不展平
        }
    }

    private MockResponse errorStatusResponse(MockRule rule) {
        int status = 500;
        Object body = null;
        if (rule.getFaultConfig() != null && !rule.getFaultConfig().isEmpty()) {
            try {
                Map<String, Object> cfg = JsonUtils.parseMap(rule.getFaultConfig());
                Object hs = cfg.get("httpStatus");
                if (hs instanceof Number) {
                    status = ((Number) hs).intValue();
                }
                body = cfg.get("body");
            } catch (Exception e) {
                log.warn("解析 fault_config 失败: {}", rule.getFaultConfig());
            }
        }
        return MockResponse.write(status, null, body);
    }

    private void applyDelay(MockRule rule) {
        long ms = computeDelayMs(rule);
        if (ms <= 0) {
            return;
        }
        try {
            // HTTP 路径保持容器线程内 sleep（TIMEOUT 故障由 Filter 层异步化，普通延迟不占额外线程）
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 计算规则延迟毫秒（0 表示无延迟；封顶 MAX_DELAY_MS），HTTP/TCP 共用 */
    private long computeDelayMs(MockRule rule) {
        String type = rule.getDelayType();
        if (type == null || "NONE".equalsIgnoreCase(type)) {
            return 0;
        }
        long ms = 0;
        if ("FIXED".equalsIgnoreCase(type)) {
            ms = rule.getDelayMs() == null ? 0 : rule.getDelayMs();
        } else if ("RANDOM".equalsIgnoreCase(type)) {
            int min = rule.getDelayMinMs() == null ? 0 : rule.getDelayMinMs();
            int max = rule.getDelayMaxMs() == null ? 0 : rule.getDelayMaxMs();
            if (max <= min) {
                ms = min;
            } else {
                ms = ThreadLocalRandom.current().nextLong(min, max + 1);
            }
        }
        return ms <= 0 ? 0 : Math.min(ms, MAX_DELAY_MS);
    }

    private MockResponse noRuleResponse(RequestSnapshot req) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "NO_RULE_MATCHED");
        body.put("method", req.getMethod());
        body.put("path", req.getPath());
        return MockResponse.write(404, null, body);
    }

    // ===================== TCP =====================

    /**
     * TCP 报文处理入口：定位接口 → 规则匹配 → 延迟/故障/渲染，返回响应字段（由 TCP 层按报文格式编码回写）。
     */
    public TcpMockResult handleTcp(Long projectId, Long listenerId, String routeValue,
                                   Map<String, Object> fields, String clientAddr) {
        ProjectSnapshot snap = cache.getProject(projectId);
        if (snap == null) {
            return TcpMockResult.notMatched(null, null, routeValue);
        }
        CompiledInterface iface = findTcpInterface(snap, listenerId, routeValue);
        if (iface == null) {
            return TcpMockResult.notMatched(projectId, null, routeValue);
        }
        RequestSnapshot rs = RequestSnapshot.builder()
                .protocol("TCP")
                .fields(fields)
                .body(fields)
                .bodyRaw(fields == null ? null : JsonUtils.toJson(fields))
                .clientAddr(clientAddr)
                .build();
        CompiledRule hit = null;
        for (CompiledRule rule : iface.getRules()) {
            if (matcher.match(rule, rs, new HashMap<>())) {
                hit = rule;
                break;
            }
        }
        if (hit == null) {
            return TcpMockResult.notMatched(projectId, iface.getId(), routeValue);
        }

        // TCP 路径引擎不 sleep：延迟值放进结果，由 TcpMockHandler 调度（见 TcpMockResult.delayMs）
        int delayMs = (int) computeDelayMs(hit.getEntity());
        String fault = hit.getEntity().getFaultType();
        if ("RESET".equalsIgnoreCase(fault)) {
            return withDelay(TcpMockResult.fault(projectId, iface.getId(), hit.getId(), TcpMockResult.Action.RESET), delayMs);
        }
        if ("TIMEOUT".equalsIgnoreCase(fault)) {
            return withDelay(TcpMockResult.fault(projectId, iface.getId(), hit.getId(), TcpMockResult.Action.TIMEOUT), delayMs);
        }

        Map<String, Object> baseVars = new HashMap<>();
        flattenFields("field", fields, baseVars);
        EvalContext ctx = new EvalContext(baseVars, secretResolver, seqProvider, projectId);
        RenderedResponse rr = renderer.render(hit.getTemplateNode(), baseVars, ctx);
        Object body = rr.getBody();
        if (!(body instanceof Map)) {
            // 渲染结果非 JSON 对象（数组/标量/null）无法编码为 TCP 报文字段，直接报错而非静默空响应
            throw new BizException(ResultCode.TEMPLATE_RENDER_ERROR,
                    "TCP 响应模板渲染结果必须为 JSON 对象，实际为: "
                            + (body == null ? "null" : body.getClass().getSimpleName()));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> respFields = (Map<String, Object>) body;
        return withDelay(TcpMockResult.write(projectId, iface.getId(), hit.getId(), respFields), delayMs);
    }

    private static TcpMockResult withDelay(TcpMockResult r, int delayMs) {
        if (delayMs > 0) {
            r.setDelayMs(delayMs);
        }
        return r;
    }

    private CompiledInterface findTcpInterface(ProjectSnapshot snap, Long listenerId, String routeValue) {
        for (CompiledInterface iface : snap.getInterfaces()) {
            if (!"TCP".equalsIgnoreCase(iface.getProtocol())) {
                continue;
            }
            if (!java.util.Objects.equals(iface.getEntity().getTcpListenerId(), listenerId)) {
                continue;
            }
            String expr = iface.getEntity().getTcpRouteExpr();
            if (expr == null || expr.isEmpty() || expr.equals(routeValue)) {
                return iface;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void flattenFields(String prefix, Map<String, Object> src, Map<String, Object> out) {
        if (src == null) {
            return;
        }
        for (Map.Entry<String, Object> e : src.entrySet()) {
            String key = prefix + "." + e.getKey();
            if (e.getValue() instanceof Map) {
                flattenFields(key, (Map<String, Object>) e.getValue(), out);
            } else {
                out.put(key, e.getValue());
            }
        }
    }

    // ============ 内部 ============

    private static final class InterfaceMatch {
        final ProjectSnapshot snapshot;
        final CompiledInterface iface;
        final Map<String, String> pathVars;

        InterfaceMatch(ProjectSnapshot snapshot, CompiledInterface iface, Map<String, String> pathVars) {
            this.snapshot = snapshot;
            this.iface = iface;
            this.pathVars = pathVars;
        }
    }

    /** 暴露给试算（evaluate）使用的渲染入口 */
    public RenderedResponse renderForEval(com.fasterxml.jackson.databind.JsonNode templateNode,
                                          Map<String, Object> baseVars, Long projectId) {
        EvalContext ctx = new EvalContext(baseVars, secretResolver, seqProvider, projectId);
        return renderer.render(templateNode, baseVars, ctx);
    }

    public List<CompiledInterface> httpInterfacesOf(Long projectId) {
        ProjectSnapshot snap = cache.getProject(projectId);
        return snap == null ? java.util.Collections.emptyList() : snap.httpInterfaces();
    }
}
