package com.miragemock.admin.service;

import com.miragemock.admin.mapper.ApiInterfaceMapper;
import com.miragemock.admin.mapper.MockRuleMapper;
import com.miragemock.common.entity.ApiInterface;
import com.miragemock.common.entity.MockRule;
import com.miragemock.common.util.JsonUtils;
import com.miragemock.core.cache.RuleCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OpenAPI 导入单测：paths 解析、方法识别、兜底规则模板生成（example/schema 骨架）、已存在跳过。
 */
class OpenApiImportServiceTest {

    private ApiInterfaceMapper interfaceMapper;
    private MockRuleMapper ruleMapper;
    private RuleCache ruleCache;
    private OpenApiImportService service;

    @BeforeEach
    void setup() {
        interfaceMapper = mock(ApiInterfaceMapper.class);
        ruleMapper = mock(MockRuleMapper.class);
        ruleCache = mock(RuleCache.class);
        service = new OpenApiImportService(interfaceMapper, ruleMapper, ruleCache);
        when(interfaceMapper.selectList(any())).thenReturn(Collections.emptyList());
    }

    private static final String SAMPLE_SPEC = "{\n"
            + "  \"openapi\": \"3.0.1\",\n"
            + "  \"info\": {\"title\": \"demo\", \"version\": \"1.0\"},\n"
            + "  \"paths\": {\n"
            + "    \"/api/user/{userId}\": {\n"
            + "      \"get\": {\n"
            + "        \"summary\": \"查询用户\",\n"
            + "        \"tags\": [\"用户\"],\n"
            + "        \"responses\": {\"200\": {\n"
            + "          \"description\": \"ok\",\n"
            + "          \"content\": {\"application/json\": {\n"
            + "            \"example\": {\"code\": \"0000\", \"data\": {\"userId\": 1, \"phone\": \"13800138000\"}}\n"
            + "          }}\n"
            + "        }}\n"
            + "      }\n"
            + "    },\n"
            + "    \"/api/order\": {\n"
            + "      \"post\": {\n"
            + "        \"responses\": {\"200\": {\"description\": \"ok\",\n"
            + "          \"content\": {\"application/json\": {\"schema\": {\"type\": \"object\", \"properties\": {\n"
            + "            \"orderId\": {\"type\": \"string\"},\n"
            + "            \"items\": {\"type\": \"array\"}\n"
            + "          }}}}\n"
            + "        }}\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}\n";

    @Test
    void importsPathsWithFallbackRules() {
        OpenApiImportService.ImportResult r =
                service.importOpenApi(1L, JsonUtils.readTree(SAMPLE_SPEC));

        assertEquals(2, r.created);
        assertEquals(0, r.skipped);
        // 两个接口 + 两个规则
        verify(interfaceMapper, times(2)).insert(any(ApiInterface.class));
        verify(ruleMapper, times(2)).insert(any(MockRule.class));
        verify(ruleCache).invalidate(1L);
    }

    @Test
    void interfaceFieldsParsed() {
        service.importOpenApi(1L, JsonUtils.readTree(SAMPLE_SPEC));
        ArgumentCaptor<ApiInterface> captor = ArgumentCaptor.forClass(ApiInterface.class);
        verify(interfaceMapper, times(2)).insert(captor.capture());
        ApiInterface first = captor.getAllValues().get(0);
        assertEquals("GET", first.getHttpMethod());
        assertEquals("/api/user/{userId}", first.getHttpPath());
        assertEquals("查询用户", first.getName());
        assertEquals(1, first.getStatus());
    }

    @Test
    void fallbackRuleTemplateUsesExample() {
        service.importOpenApi(1L, JsonUtils.readTree(SAMPLE_SPEC));
        ArgumentCaptor<MockRule> captor = ArgumentCaptor.forClass(MockRule.class);
        verify(ruleMapper, times(2)).insert(captor.capture());
        MockRule first = captor.getAllValues().get(0);
        assertEquals("openapi-fallback", first.getName());
        assertEquals("[]", first.getMatchCondition());
        // example 原样作为模板
        assertTrue(first.getResponseTemplate().contains("\"13800138000\""));
        assertTrue(first.getResponseTemplate().contains("\"0000\""));
    }

    @Test
    void schemaWithoutExampleGeneratesSkeleton() {
        service.importOpenApi(1L, JsonUtils.readTree(SAMPLE_SPEC));
        ArgumentCaptor<MockRule> captor = ArgumentCaptor.forClass(MockRule.class);
        verify(ruleMapper, times(2)).insert(captor.capture());
        MockRule second = captor.getAllValues().get(1); // /api/order POST（无 example，走 schema 骨架）
        assertTrue(second.getResponseTemplate().contains("\"orderId\":\"\""));
        assertTrue(second.getResponseTemplate().contains("\"items\":[]"));
    }

    @Test
    void existingInterfaceSkipped() {
        ApiInterface exist = new ApiInterface();
        exist.setHttpMethod("GET");
        exist.setHttpPath("/api/user/{userId}");
        when(interfaceMapper.selectList(any())).thenReturn(Collections.singletonList(exist));

        OpenApiImportService.ImportResult r =
                service.importOpenApi(1L, JsonUtils.readTree(SAMPLE_SPEC));
        assertEquals(1, r.created);
        assertEquals(1, r.skipped);
        verify(ruleCache, never()).invalidate(0L); // 只对存在的项目作废；此处 invalidate(1L) 仅当 created>0
        verify(ruleCache).invalidate(1L);
    }
}
