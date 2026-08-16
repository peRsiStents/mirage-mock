package com.miragemock.admin.service;

import com.miragemock.admin.dto.RunResult;
import com.miragemock.admin.security.TestTargetGuard;
import com.miragemock.admin.security.TestTargetProperties;
import com.miragemock.common.entity.TestCase;
import com.miragemock.common.util.JsonUtils;
import com.miragemock.dsl.eval.ExpressionEvaluator;
import com.miragemock.dsl.func.BuiltinFunctions;
import com.miragemock.dsl.func.FunctionRegistry;
import com.miragemock.tcp.codec.MessageParserRegistry;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 断言增强集成测试：本地 HttpServer 真实执行用例，覆盖 regex / notContains / notEmpty /
 * notExists / arrayLength 操作符与 header 分支增强。
 */
class AssertionEnhancementTest {

    private static HttpServer server;
    private static String baseUrl;
    private static TestCaseService service;

    private static final String BODY = "{\"code\":\"0000\",\"data\":{"
            + "\"phone\":\"13800138000\",\"items\":[\"a\",\"b\",\"c\"],\"age\":25,\"nick\":\"\"}}";

    @BeforeAll
    static void setup() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/test", exchange -> {
            byte[] resp = BODY.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/test";

        FunctionRegistry reg = new FunctionRegistry();
        BuiltinFunctions.all().forEach(reg::register);
        ExpressionEvaluator evaluator = new ExpressionEvaluator(reg);
        service = new TestCaseService(null, null, null, null, null, null,
                new RestTemplate(), evaluator, null, null,
                new MessageParserRegistry(null, Collections.emptyList()),
                new TestTargetGuard(new TestTargetProperties()), null);
    }

    @AfterAll
    static void teardown() {
        server.stop(0);
    }

    @SuppressWarnings("unchecked")
    private RunResult run(String assertionsJson) {
        TestCase tc = new TestCase();
        tc.setProjectId(1L);
        tc.setProtocol("HTTP");
        tc.setMethod("GET");
        tc.setUrl(baseUrl);
        tc.setBody("");
        tc.setAssertions(assertionsJson);
        RunResult rr = service.executeCase(tc,
                service.newContext(1L, new java.util.HashMap<>()));
        return rr;
    }

    private List<Map<String, Object>> assertions(RunResult rr) {
        return (List<Map<String, Object>>) rr.getAssertions();
    }

    @Test
    void regex_phoneMatches() {
        RunResult rr = run("[{\"type\":\"jsonPath\",\"target\":\"$.data.phone\",\"op\":\"regex\",\"expected\":\"^1[3-9]\\\\d{9}$\"}]");
        assertTrue(Boolean.TRUE.equals(rr.getPassed()), rr.getError());
        assertTrue((Boolean) assertions(rr).get(0).get("passed"));
    }

    @Test
    void regex_codePrefix() {
        RunResult rr = run("[{\"type\":\"jsonPath\",\"target\":\"$.code\",\"op\":\"regex\",\"expected\":\"^00\\\\d{2}$\"}]");
        assertTrue(Boolean.TRUE.equals(rr.getPassed()));
    }

    @Test
    void arrayLength_eq() {
        RunResult rr = run("[{\"type\":\"jsonPath\",\"target\":\"$.data.items\",\"op\":\"arrayLength\",\"expected\":\"3\"}]");
        assertTrue(Boolean.TRUE.equals(rr.getPassed()), String.valueOf(assertions(rr).get(0)));
    }

    @Test
    void arrayLength_wrongCountFails() {
        RunResult rr = run("[{\"type\":\"jsonPath\",\"target\":\"$.data.items\",\"op\":\"arrayLength\",\"expected\":\"5\"}]");
        assertFalse(Boolean.TRUE.equals(rr.getPassed()));
        assertFalse((Boolean) assertions(rr).get(0).get("passed"));
    }

    @Test
    void notContains() {
        RunResult rr = run("[{\"type\":\"jsonPath\",\"target\":\"$.data.phone\",\"op\":\"notContains\",\"expected\":\"999\"}]");
        assertTrue(Boolean.TRUE.equals(rr.getPassed()));
    }

    @Test
    void notEmpty_onFilledValue() {
        RunResult rr = run("[{\"type\":\"jsonPath\",\"target\":\"$.data.age\",\"op\":\"notEmpty\",\"expected\":\"\"}]");
        assertTrue(Boolean.TRUE.equals(rr.getPassed()));
    }

    @Test
    void notEmpty_onEmptyStringFails() {
        RunResult rr = run("[{\"type\":\"jsonPath\",\"target\":\"$.data.nick\",\"op\":\"notEmpty\",\"expected\":\"\"}]");
        assertFalse(Boolean.TRUE.equals(rr.getPassed()));
    }

    @Test
    void notExists_onMissingField() {
        RunResult rr = run("[{\"type\":\"jsonPath\",\"target\":\"$.data.missing\",\"op\":\"notExists\",\"expected\":\"\"}]");
        assertTrue(Boolean.TRUE.equals(rr.getPassed()));
    }

    @Test
    void header_regex() {
        RunResult rr = run("[{\"type\":\"header\",\"target\":\"Content-Type\",\"op\":\"regex\",\"expected\":\"application/json.*\"}]");
        assertTrue(Boolean.TRUE.equals(rr.getPassed()), String.valueOf(assertions(rr).get(0)));
    }

    @Test
    void header_notContains() {
        RunResult rr = run("[{\"type\":\"header\",\"target\":\"Content-Type\",\"op\":\"notContains\",\"expected\":\"text/html\"}]");
        assertTrue(Boolean.TRUE.equals(rr.getPassed()));
    }

    @Test
    void allEnhancedAssertionsCombined() {
        String json = "["
                + "{\"type\":\"jsonPath\",\"target\":\"$.data.phone\",\"op\":\"regex\",\"expected\":\"^1[3-9]\\\\d{9}$\"},"
                + "{\"type\":\"jsonPath\",\"target\":\"$.data.items\",\"op\":\"arrayLength\",\"expected\":\"3\"},"
                + "{\"type\":\"jsonPath\",\"target\":\"$.code\",\"op\":\"notContains\",\"expected\":\"999\"},"
                + "{\"type\":\"jsonPath\",\"target\":\"$.data.age\",\"op\":\"notEmpty\",\"expected\":\"\"},"
                + "{\"type\":\"status\",\"op\":\"eq\",\"expected\":\"200\"}"
                + "]";
        RunResult rr = run(json);
        assertTrue(Boolean.TRUE.equals(rr.getPassed()), rr.getError());
        assertEquals(5, assertions(rr).size());
        for (Map<String, Object> a : assertions(rr)) {
            assertTrue((Boolean) a.get("passed"), "断言未通过: " + a);
        }
    }
}
