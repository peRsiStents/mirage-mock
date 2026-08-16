package com.miragemock.core.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.miragemock.common.entity.ApiInterface;
import com.miragemock.common.entity.MockRule;
import com.miragemock.common.model.MatchCondition;
import com.miragemock.common.util.JsonUtils;
import com.miragemock.core.cache.CompiledInterface;
import com.miragemock.core.cache.CompiledRule;
import com.miragemock.core.cache.ProjectSnapshot;
import com.miragemock.core.cache.RuleCache;
import com.miragemock.core.cache.RuleSnapshotLoader;
import com.miragemock.core.match.MatchConditionEvaluator;
import com.miragemock.core.match.RequestSnapshot;
import com.miragemock.core.render.TemplateRenderer;
import com.miragemock.dsl.eval.ExpressionEvaluator;
import com.miragemock.dsl.func.BuiltinFunctions;
import com.miragemock.dsl.func.FunctionRegistry;
import com.miragemock.dsl.spi.SeqProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mock 运行时内核单测：HTTP 路由/规则命中/兜底/未命中，TCP 两阶段匹配与渲染，延迟计算，缓存热刷新。
 * 通过内存 RuleSnapshotLoader 测试替身构造，不依赖 Spring 与数据库。
 */
class MockEngineTest {

    private RuleCache cache;
    private MockEngine engine;
    private MockRule vipRule;
    private MockRule fallbackRule;
    private MockRule tcpRule;

    @BeforeEach
    void setup() {
        vipRule = rule(10L, 10, "[{\"source\":\"header\",\"key\":\"x-env\",\"op\":\"eq\",\"value\":\"gray\"}]",
                "{\"status\":200,\"body\":{\"userId\":\"${path.userId}\",\"level\":\"VIP\"}}",
                null, null, null, null, "NONE");
        fallbackRule = rule(11L, 100, "[]",
                "{\"status\":200,\"body\":{\"userId\":\"${path.userId}\",\"level\":\"NORMAL\"}}",
                null, null, null, null, "NONE");
        tcpRule = rule(20L, 10,
                "[{\"source\":\"field\",\"key\":\"$.amount\",\"op\":\"lte\",\"value\":50000}]",
                "{\"transCode\":\"0210\",\"respCode\":\"00\",\"amount\":\"${field.amount}\"}",
                null, null, null, null, "NONE");

        RuleSnapshotLoader loader = new RuleSnapshotLoader() {
            @Override
            public Map<Long, ProjectSnapshot> loadAll() {
                return Collections.singletonMap(1L, snapshot());
            }

            @Override
            public ProjectSnapshot loadProject(long projectId) {
                return projectId == 1L ? snapshot() : null;
            }

            @Override
            public Long resolveProjectIdByCode(String code) {
                return "demo".equals(code) ? 1L : null;
            }

            @Override
            public List<Long> allProjectIds() {
                return Collections.singletonList(1L);
            }
        };
        cache = new RuleCache(loader);
        cache.reloadAll();

        FunctionRegistry reg = new FunctionRegistry();
        BuiltinFunctions.all().forEach(reg::register);
        ExpressionEvaluator evaluator = new ExpressionEvaluator(reg);
        TemplateRenderer renderer = new TemplateRenderer(evaluator);
        MatchConditionEvaluator matcher = new MatchConditionEvaluator();
        SeqProvider seq = (projectId, name, startValue) -> startValue + 1;
        engine = new MockEngine(cache, matcher, renderer, null, seq);
    }

    private ProjectSnapshot snapshot() {
        ApiInterface httpIface = new ApiInterface();
        httpIface.setId(100L);
        httpIface.setProjectId(1L);
        httpIface.setProtocol("HTTP");
        httpIface.setHttpMethod("GET");
        httpIface.setHttpPath("/api/user/{userId}");
        httpIface.setRecordMode(2); // 回放
        httpIface.setUpstreamUrl("http://upstream:8080");
        CompiledInterface http = new CompiledInterface(httpIface,
                Arrays.asList(compiled(vipRule), compiled(fallbackRule)));

        ApiInterface tcpIface = new ApiInterface();
        tcpIface.setId(200L);
        tcpIface.setProjectId(1L);
        tcpIface.setProtocol("TCP");
        tcpIface.setTcpListenerId(9001L);
        tcpIface.setTcpRouteExpr("0200");
        CompiledInterface tcp = new CompiledInterface(tcpIface,
                Collections.singletonList(compiled(tcpRule)));

        return new ProjectSnapshot(1L, "demo", Arrays.asList(http, tcp));
    }

    private MockRule rule(Long id, int priority, String condJson, String tplJson,
                          String delayType, Integer delayMs, Integer min, Integer max, String faultType) {
        MockRule r = new MockRule();
        r.setId(id);
        r.setPriority(priority);
        r.setMatchCondition(condJson);
        r.setResponseTemplate(tplJson);
        r.setDelayType(delayType);
        r.setDelayMs(delayMs);
        r.setDelayMinMs(min);
        r.setDelayMaxMs(max);
        r.setFaultType(faultType);
        return r;
    }

    private CompiledRule compiled(MockRule rule) {
        List<MatchCondition> conds = JsonUtils.mapper().convertValue(
                JsonUtils.readTree(rule.getMatchCondition()),
                JsonUtils.mapper().getTypeFactory().constructCollectionType(List.class, MatchCondition.class));
        JsonNode tpl = JsonUtils.readTree(rule.getResponseTemplate());
        return new CompiledRule(rule, conds, tpl);
    }

    private RequestSnapshot httpReq(String path, String envHeader) {
        Map<String, String> headers = new HashMap<>();
        if (envHeader != null) {
            headers.put("x-env", envHeader);
        }
        return RequestSnapshot.builder()
                .protocol("HTTP")
                .method("GET")
                .path(path)
                .headers(headers)
                .clientAddr("127.0.0.1")
                .build();
    }

    // ============ HTTP ============

    @Test
    void http_hitsFallbackRule() {
        HttpMockResult r = engine.handleHttp(httpReq("/api/user/10086", null));
        assertTrue(r.isMatched());
        assertEquals(200, r.getResponse().getStatus());
        Map<String, Object> body = (Map<String, Object>) r.getResponse().getBody();
        assertEquals("10086", body.get("userId"));
        assertEquals("NORMAL", body.get("level"));
    }

    @Test
    void http_hitsVipRule_byPriority() {
        HttpMockResult r = engine.handleHttp(httpReq("/api/user/10086", "gray"));
        assertTrue(r.isMatched());
        assertEquals(10L, r.getRuleId());
        Map<String, Object> body = (Map<String, Object>) r.getResponse().getBody();
        assertEquals("VIP", body.get("level"));
    }

    @Test
    void http_pathVarInjected() {
        HttpMockResult r = engine.handleHttp(httpReq("/api/user/77777", null));
        Map<String, Object> body = (Map<String, Object>) r.getResponse().getBody();
        assertEquals("77777", body.get("userId"));
    }

    @Test
    void http_noRuleMatched_404() {
        HttpMockResult r = engine.handleHttp(httpReq("/api/nonexistent", null));
        assertFalse(r.isMatched());
        assertEquals(404, r.getResponse().getStatus());
        Map<String, Object> body = (Map<String, Object>) r.getResponse().getBody();
        assertEquals("NO_RULE_MATCHED", body.get("error"));
    }

    @Test
    void http_methodMismatch_notMatched() {
        RequestSnapshot req = RequestSnapshot.builder()
                .protocol("HTTP").method("POST").path("/api/user/1")
                .headers(new HashMap<>()).clientAddr("127.0.0.1").build();
        HttpMockResult r = engine.handleHttp(req);
        assertFalse(r.isMatched());
    }

    // ============ TCP 两阶段 ============

    @Test
    void tcp_matchAndRender() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("transCode", "0200");
        fields.put("amount", "1000");
        MockEngine.TcpMatch match = engine.matchTcp(1L, 9001L, "0200", fields, "127.0.0.1");
        assertTrue(match.isMatched());
        assertEquals(0, match.getDelayMs());
        TcpMockResult result = engine.renderTcp(match);
        assertTrue(result.isMatched());
        assertEquals("00", result.getFields().get("respCode"));
        // ${field.amount} 回显请求字段
        assertEquals("1000", result.getFields().get("amount"));
    }

    @Test
    void tcp_ruleNotMatched() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("transCode", "0200");
        fields.put("amount", "99999"); // 超过 50000，规则不命中
        MockEngine.TcpMatch match = engine.matchTcp(1L, 9001L, "0200", fields, "127.0.0.1");
        assertFalse(match.isMatched());
        assertNull(match.getRule());
    }

    @Test
    void tcp_unknownProjectOrRoute() {
        assertFalse(engine.matchTcp(999L, 9001L, "0200", new HashMap<>(), "x").isMatched());
        assertFalse(engine.matchTcp(1L, 9001L, "9999", new HashMap<>(), "x").isMatched());
    }

    // ============ 延迟计算 ============

    @Test
    void delay_fixed() {
        MockRule r = rule(1L, 10, "[]", "{}", "FIXED", 120, null, null, "NONE");
        assertEquals(120, MockEngine.computeDelayMs(r));
    }

    @Test
    void delay_randomWithinBounds() {
        MockRule r = rule(1L, 10, "[]", "{}", "RANDOM", null, 50, 60, "NONE");
        for (int i = 0; i < 100; i++) {
            long ms = MockEngine.computeDelayMs(r);
            assertTrue(ms >= 50 && ms <= 60, "超出区间: " + ms);
        }
    }

    @Test
    void delay_noneOrNull() {
        assertEquals(0, MockEngine.computeDelayMs(rule(1L, 10, "[]", "{}", "NONE", null, null, null, "NONE")));
        assertEquals(0, MockEngine.computeDelayMs(rule(1L, 10, "[]", "{}", null, null, null, null, "NONE")));
        assertEquals(0, MockEngine.computeDelayMs(null));
    }

    @Test
    void delay_cappedAt60s() {
        MockRule r = rule(1L, 10, "[]", "{}", "FIXED", 300_000, null, null, "NONE");
        assertEquals(60_000, MockEngine.computeDelayMs(r));
    }

    // ============ 缓存热刷新 ============

    @Test
    void cache_invalidateReloads() {
        // 修改 fallback 模板后 invalidate，应即时生效
        fallbackRule.setResponseTemplate(
                "{\"status\":200,\"body\":{\"userId\":\"${path.userId}\",\"level\":\"UPDATED\"}}");
        cache.invalidate(1L);
        HttpMockResult r = engine.handleHttp(httpReq("/api/user/1", null));
        Map<String, Object> body = (Map<String, Object>) r.getResponse().getBody();
        assertEquals("UPDATED", body.get("level"));
    }

    @Test
    void cache_singleProjectDefault() {
        // 仅一个项目时，无 header 也能命中
        HttpMockResult r = engine.handleHttp(httpReq("/api/user/1", null));
        assertTrue(r.isMatched());
        assertNotNull(r.getProjectId());
    }

    // ============ 录制回放代理提示 ============

    @Test
    void proxyHint_resolved() {
        MockEngine.ProxyHint hint = engine.resolveProxyHint(httpReq("/api/user/1", null));
        assertNotNull(hint);
        assertEquals(1L, hint.getProjectId());
        assertEquals(100L, hint.getInterfaceId());
        assertEquals(2, hint.getRecordMode());
        assertEquals("http://upstream:8080", hint.getUpstreamUrl());
    }

    @Test
    void proxyHint_unknownPath_null() {
        assertNull(engine.resolveProxyHint(httpReq("/api/nonexistent", null)));
    }

    @Test
    void proxyHint_independentOfRuleMatch() {
        // 规则是否命中不影响代理提示解析（代理仅在未命中时被 HTTP 层使用）
        MockEngine.ProxyHint withGray = engine.resolveProxyHint(httpReq("/api/user/1", "gray"));
        MockEngine.ProxyHint withoutGray = engine.resolveProxyHint(httpReq("/api/user/1", null));
        assertNotNull(withGray);
        assertEquals(withGray.getInterfaceId(), withoutGray.getInterfaceId());
    }
}
