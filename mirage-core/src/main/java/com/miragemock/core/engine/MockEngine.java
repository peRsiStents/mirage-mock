package com.miragemock.core.engine;

import com.miragemock.common.constant.Constants;
import com.miragemock.common.entity.MockRule;
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
        ProjectSnapshot snap = resolveProject(req);
        if (snap == null) {
            return HttpMockResult.notMatched(null, null, noRuleResponse(req));
        }
        InterfaceMatch im = matchInterface(snap, req);
        if (im == null) {
            return HttpMockResult.notMatched(snap.getProjectId(), null, noRuleResponse(req));
        }

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

    private InterfaceMatch matchInterface(ProjectSnapshot snap, RequestSnapshot req) {
        String method = req.getMethod() == null ? "" : req.getMethod().toUpperCase();
        for (CompiledInterface iface : snap.httpInterfaces()) {
            String m = iface.getEntity().getHttpMethod();
            boolean methodOk = m == null || m.isEmpty() || "ANY".equalsIgnoreCase(m) || m.equalsIgnoreCase(method);
            if (!methodOk) {
                continue;
            }
            String pattern = iface.getEntity().getHttpPath();
            if (pattern == null || !pathMatcher.match(pattern, req.getPath())) {
                continue;
            }
            Map<String, String> vars = pathMatcher.extractUriTemplateVariables(pattern, req.getPath());
            return new InterfaceMatch(iface, vars);
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
        EvalContext ctx = new EvalContext(baseVars, secretResolver, seqProvider, snap.getProjectId());
        RenderedResponse rr = renderer.render(rule.getTemplateNode(), baseVars, ctx);
        return MockResponse.write(rr.getStatus(), rr.getHeaders(), rr.getBody());
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
        String type = rule.getDelayType();
        if (type == null || "NONE".equalsIgnoreCase(type)) {
            return;
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
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(Math.min(ms, MAX_DELAY_MS));
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
            if (matcher.match(rule.getConditions(), rs, new HashMap<>())) {
                hit = rule;
                break;
            }
        }
        if (hit == null) {
            return TcpMockResult.notMatched(projectId, iface.getId(), routeValue);
        }

        applyDelay(hit.getEntity());
        String fault = hit.getEntity().getFaultType();
        if ("RESET".equalsIgnoreCase(fault)) {
            return TcpMockResult.fault(projectId, iface.getId(), hit.getId(), TcpMockResult.Action.RESET);
        }
        if ("TIMEOUT".equalsIgnoreCase(fault)) {
            return TcpMockResult.fault(projectId, iface.getId(), hit.getId(), TcpMockResult.Action.TIMEOUT);
        }

        Map<String, Object> baseVars = new HashMap<>();
        flattenFields("field", fields, baseVars);
        EvalContext ctx = new EvalContext(baseVars, secretResolver, seqProvider, projectId);
        RenderedResponse rr = renderer.render(hit.getTemplateNode(), baseVars, ctx);
        Object body = rr.getBody();
        Map<String, Object> respFields = (body instanceof Map)
                ? (Map<String, Object>) body
                : body == null ? new LinkedHashMap<>() : new LinkedHashMap<>();
        return TcpMockResult.write(projectId, iface.getId(), hit.getId(), respFields);
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
        final CompiledInterface iface;
        final Map<String, String> pathVars;

        InterfaceMatch(CompiledInterface iface, Map<String, String> pathVars) {
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
