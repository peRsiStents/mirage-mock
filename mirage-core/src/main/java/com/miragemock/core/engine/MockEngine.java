package com.miragemock.core.engine;

import com.miragemock.common.constant.Constants;
import com.miragemock.common.entity.ApiInterface;
import com.miragemock.common.entity.MockRule;
import com.miragemock.common.enums.DelayType;
import com.miragemock.common.enums.FaultType;
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
        InterfaceMatch im = resolveHttpInterface(req);
        if (im == null) {
            ProjectSnapshot snap = resolveProject(req);
            return HttpMockResult.notMatched(snap == null ? null : snap.getProjectId(), null, noRuleResponse(req));
        }
        ProjectSnapshot snap = im.snapshot;

        CompiledRule hit = null;
        for (CompiledRule rule : im.iface.getRules()) {
            if (matcher.match(rule.getConditions(), req, im.pathVars)) {
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

    /**
     * 定位 HTTP 接口（与 handleHttp 同一套路由逻辑）：
     * 项目内路由 → 无标识跨项目唯一路径 → 歧义/不存在返回 null。
     */
    private InterfaceMatch resolveHttpInterface(RequestSnapshot req) {
        ProjectSnapshot snap = resolveProject(req);
        boolean hasProjectHeader = req.getHeaders() != null
                && req.getHeaders().get(Constants.HEADER_PROJECT_CODE.toLowerCase()) != null
                && !req.getHeaders().get(Constants.HEADER_PROJECT_CODE.toLowerCase()).isEmpty();
        if (snap != null) {
            return matchInterface(snap, req);
        }
        if (!hasProjectHeader) {
            return resolveByUniquePath(req);
        }
        return null;
    }

    /**
     * 录制回放配置提示：未命中规则时由 HTTP 层查询该接口的代理配置（录制模式 + 上游地址）。
     *
     * @return 接口存在且为 HTTP 时返回其代理配置；否则 null
     */
    public ProxyHint resolveProxyHint(RequestSnapshot req) {
        InterfaceMatch im = resolveHttpInterface(req);
        if (im == null) {
            return null;
        }
        ApiInterface entity = im.iface.getEntity();
        Integer mode = entity.getRecordMode();
        return new ProxyHint(im.snapshot.getProjectId(), im.iface.getId(),
                mode == null ? 0 : mode, entity.getUpstreamUrl());
    }

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
        if (pattern == null || !pathMatcher.match(pattern, path)) {
            return null;
        }
        return pathMatcher.extractUriTemplateVariables(pattern, path);
    }

    // ============ 延迟 / 故障注入 / 渲染 ============

    private MockResponse applyFaultOrRender(CompiledRule rule, RequestSnapshot req,
                                             Map<String, String> pathVars, ProjectSnapshot snap) {
        applyDelay(rule.getEntity());
        FaultType fault = parseEnum(FaultType.class, rule.getEntity().getFaultType(), FaultType.NONE);
        switch (fault) {
            case RESET:
                return MockResponse.reset();
            case TIMEOUT:
                return MockResponse.timeout();
            case ERROR_STATUS:
                return errorStatusResponse(rule.getEntity());
            default:
                break;
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

    /**
     * 计算规则延迟毫秒数（纯计算，不阻塞调用线程）。
     * TCP 路径由调用方在 Netty 事件循环上异步调度延迟；HTTP 路径由 {@link #applyDelay} 同步 sleep。
     */
    public static long computeDelayMs(MockRule rule) {
        if (rule == null) {
            return 0;
        }
        DelayType type = parseEnum(DelayType.class, rule.getDelayType(), DelayType.NONE);
        long ms;
        switch (type) {
            case FIXED:
                ms = rule.getDelayMs() == null ? 0 : rule.getDelayMs();
                break;
            case RANDOM: {
                int min = rule.getDelayMinMs() == null ? 0 : rule.getDelayMinMs();
                int max = rule.getDelayMaxMs() == null ? 0 : rule.getDelayMaxMs();
                ms = max <= min ? min : ThreadLocalRandom.current().nextLong(min, max + 1);
                break;
            }
            default:
                ms = 0;
        }
        return Math.min(ms, MAX_DELAY_MS);
    }

    /** HTTP 路径：延迟在 Servlet 工作线程上同步 sleep（每请求独立线程，可接受）。 */
    private void applyDelay(MockRule rule) {
        long ms = computeDelayMs(rule);
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private MockResponse noRuleResponse(RequestSnapshot req) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "NO_RULE_MATCHED");
        body.put("method", req.getMethod());
        body.put("path", req.getPath());
        return MockResponse.write(404, null, body);
    }

    /** 兼容存量字符串配置的枚举解析：非法/缺失值回退默认值 */
    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, E defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }

    // ===================== TCP =====================

    /**
     * TCP 报文处理·阶段一：定位接口 → 规则匹配 → 计算延迟。
     *
     * <p>本阶段不做任何阻塞操作（不 sleep、不渲染），由调用方（TCP 层）根据返回的
     * {@link TcpMatch#getDelayMs()} 在事件循环上异步调度阶段二，避免阻塞 Netty EventLoop。</p>
     */
    public TcpMatch matchTcp(Long projectId, Long listenerId, String routeValue,
                             Map<String, Object> fields, String clientAddr) {
        ProjectSnapshot snap = cache.getProject(projectId);
        if (snap == null) {
            return TcpMatch.notMatched(null, null, routeValue);
        }
        CompiledInterface iface = findTcpInterface(snap, listenerId, routeValue);
        if (iface == null) {
            return TcpMatch.notMatched(projectId, null, routeValue);
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
            if (matcher.match(rule.getConditions(), rs, new HashMap<>())) {
                hit = rule;
                break;
            }
        }
        if (hit == null) {
            return TcpMatch.notMatched(projectId, iface.getId(), routeValue);
        }
        long delayMs = computeDelayMs(hit.getEntity());
        return new TcpMatch(true, projectId, iface.getId(), hit.getId(), hit, fields, delayMs);
    }

    /**
     * TCP 报文处理·阶段二：故障注入 + 模板渲染。
     *
     * <p>由调用方在延迟结束后（或延迟为 0 时立即）调用，此时线程为事件循环线程，本方法
     * 仅做 CPU 渲染（微秒~毫秒级），不再包含任何 sleep。</p>
     */
    public TcpMockResult renderTcp(TcpMatch match) {
        MockRule rule = match.getRule().getEntity();
        FaultType fault = parseEnum(FaultType.class, rule.getFaultType(), FaultType.NONE);
        switch (fault) {
            case RESET:
                return TcpMockResult.fault(match.getProjectId(), match.getInterfaceId(), match.getRuleId(),
                        TcpMockResult.Action.RESET);
            case TIMEOUT:
                return TcpMockResult.fault(match.getProjectId(), match.getInterfaceId(), match.getRuleId(),
                        TcpMockResult.Action.TIMEOUT);
            default:
                break;
        }
        Map<String, Object> baseVars = new HashMap<>();
        flattenFields("field", match.getFields(), baseVars);
        EvalContext ctx = new EvalContext(baseVars, secretResolver, seqProvider, match.getProjectId());
        RenderedResponse rr = renderer.render(match.getRule().getTemplateNode(), baseVars, ctx);
        Object body = rr.getBody();
        Map<String, Object> respFields = (body instanceof Map)
                ? (Map<String, Object>) body
                : body == null ? new LinkedHashMap<>() : new LinkedHashMap<>();
        return TcpMockResult.write(match.getProjectId(), match.getInterfaceId(), match.getRuleId(), respFields);
    }

    /**
     * TCP 匹配结果（阶段一产物）：携带命中规则、请求字段与延迟毫秒数，供阶段二渲染。
     */
    public static final class TcpMatch {
        private final boolean matched;
        private final Long projectId;
        private final Long interfaceId;
        private final Long ruleId;
        private final CompiledRule rule;
        private final Map<String, Object> fields;
        private final long delayMs;

        private TcpMatch(boolean matched, Long projectId, Long interfaceId, Long ruleId,
                         CompiledRule rule, Map<String, Object> fields, long delayMs) {
            this.matched = matched;
            this.projectId = projectId;
            this.interfaceId = interfaceId;
            this.ruleId = ruleId;
            this.rule = rule;
            this.fields = fields;
            this.delayMs = delayMs;
        }

        public static TcpMatch notMatched(Long projectId, Long interfaceId, String routeValue) {
            return new TcpMatch(false, projectId, interfaceId, null, null, null, 0);
        }

        public boolean isMatched() {
            return matched;
        }

        public Long getProjectId() {
            return projectId;
        }

        public Long getInterfaceId() {
            return interfaceId;
        }

        public Long getRuleId() {
            return ruleId;
        }

        /** 命中规则（仅 matched 时非空） */
        public CompiledRule getRule() {
            return rule;
        }

        /** 请求解析字段（仅 matched 时非空） */
        public Map<String, Object> getFields() {
            return fields;
        }

        /** 规则延迟毫秒数，调用方应在事件循环上异步调度阶段二 */
        public long getDelayMs() {
            return delayMs;
        }
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

    /**
     * 录制回放代理配置提示（HTTP 层未命中规则时使用）。
     */
    public static final class ProxyHint {
        private final Long projectId;
        private final Long interfaceId;
        private final int recordMode;
        private final String upstreamUrl;

        public ProxyHint(Long projectId, Long interfaceId, int recordMode, String upstreamUrl) {
            this.projectId = projectId;
            this.interfaceId = interfaceId;
            this.recordMode = recordMode;
            this.upstreamUrl = upstreamUrl;
        }

        public Long getProjectId() {
            return projectId;
        }

        public Long getInterfaceId() {
            return interfaceId;
        }

        /** 0=关闭 1=录制 2=回放 */
        public int getRecordMode() {
            return recordMode;
        }

        public String getUpstreamUrl() {
            return upstreamUrl;
        }
    }

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
        return renderForEval(templateNode, baseVars, projectId, false);
    }

    /**
     * 试算渲染入口（strict 开启后：表达式主体位置的未知变量直接抛错，便于用户在保存前发现拼写错误）。
     */
    public RenderedResponse renderForEval(com.fasterxml.jackson.databind.JsonNode templateNode,
                                          Map<String, Object> baseVars, Long projectId, boolean strict) {
        EvalContext ctx = new EvalContext(baseVars, secretResolver, seqProvider, projectId);
        if (strict) {
            ctx.setStrict(true);
        }
        return renderer.render(templateNode, baseVars, ctx);
    }

    public List<CompiledInterface> httpInterfacesOf(Long projectId) {
        ProjectSnapshot snap = cache.getProject(projectId);
        return snap == null ? java.util.Collections.emptyList() : snap.httpInterfaces();
    }
}
