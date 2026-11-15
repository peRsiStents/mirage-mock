package com.miragemock.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.miragemock.admin.mapper.ApiInterfaceMapper;
import com.miragemock.admin.mapper.MockRuleMapper;
import com.miragemock.common.api.ResultCode;
import com.miragemock.common.constant.Constants;
import com.miragemock.common.entity.ApiInterface;
import com.miragemock.common.entity.MockRule;
import com.miragemock.common.exception.BizException;
import com.miragemock.common.util.JsonUtils;
import com.miragemock.core.cache.RuleCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAPI 3.x 导入：解析 spec.paths → 批量创建 HTTP 接口 + 默认兜底规则。
 *
 * <p>手写轻量解析器（避免引入 swagger-parser 依赖）：支持 paths 下 get/post/put/delete/patch/head/options，
 * 路径模板 {var} 原样保留（与 AntPathMatcher 通配兼容）；operation.summary 作为接口名、tags 首项作为备注。</p>
 */
@Service
public class OpenApiImportService {

    private static final Logger log = LoggerFactory.getLogger(OpenApiImportService.class);

    private static final String[] HTTP_METHODS =
            {"get", "post", "put", "delete", "patch", "head", "options"};

    private final ApiInterfaceMapper interfaceMapper;
    private final MockRuleMapper ruleMapper;
    private final RuleCache ruleCache;

    @Autowired
    public OpenApiImportService(ApiInterfaceMapper interfaceMapper, MockRuleMapper ruleMapper,
                                RuleCache ruleCache) {
        this.interfaceMapper = interfaceMapper;
        this.ruleMapper = ruleMapper;
        this.ruleCache = ruleCache;
    }

    /**
     * 导入结果摘要。
     */
    public static final class ImportResult {
        public int created = 0;
        public int skipped = 0;
        public final List<String> messages = new ArrayList<>();
    }

    /**
     * 解析 OpenAPI 3 JSON 并批量建接口与兜底规则。
     *
     * @param spec 已解析的 OpenAPI 文档（JsonNode）
     * @return 导入摘要（已存在的方法+路径跳过）
     */
    @Transactional
    public ImportResult importOpenApi(Long projectId, JsonNode spec) {
        if (projectId == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "缺少 projectId");
        }
        if (spec == null || !spec.isObject()) {
            throw new BizException(ResultCode.BAD_REQUEST, "spec 必须是 JSON 对象（OpenAPI 3 文档）");
        }
        JsonNode paths = spec.get("paths");
        if (paths == null || !paths.isObject()) {
            throw new BizException(ResultCode.BAD_REQUEST, "spec 缺少 paths 节点");
        }

        // 已存在的 (method,path) 集合，避免重复创建
        java.util.Set<String> existing = new java.util.HashSet<>();
        for (ApiInterface i : interfaceMapper.selectList(new LambdaQueryWrapper<ApiInterface>()
                .eq(ApiInterface::getProjectId, projectId)
                .eq(ApiInterface::getProtocol, "HTTP"))) {
            if (i.getHttpMethod() != null && i.getHttpPath() != null) {
                existing.add(i.getHttpMethod().toUpperCase() + " " + i.getHttpPath());
            }
        }

        ImportResult result = new ImportResult();
        Iterator<Map.Entry<String, JsonNode>> pathIt = paths.fields();
        while (pathIt.hasNext()) {
            Map.Entry<String, JsonNode> pathEntry = pathIt.next();
            String path = pathEntry.getKey();
            JsonNode ops = pathEntry.getValue();
            if (ops == null || !ops.isObject()) {
                continue;
            }
            for (String method : HTTP_METHODS) {
                JsonNode op = ops.get(method);
                if (op == null) {
                    continue;
                }
                String m = method.toUpperCase();
                String key = m + " " + path;
                if (existing.contains(key)) {
                    result.skipped++;
                    result.messages.add("已存在，跳过: " + key);
                    continue;
                }
                try {
                    ApiInterface iface = buildInterface(projectId, m, path, op);
                    interfaceMapper.insert(iface);
                    MockRule rule = buildFallbackRule(iface.getId(), path, op);
                    ruleMapper.insert(rule);
                    existing.add(key);
                    result.created++;
                    result.messages.add("已创建: " + key);
                } catch (Exception e) {
                    log.warn("导入接口失败 {} {}: {}", m, path, e.getMessage());
                    result.messages.add("创建失败: " + key + "（" + e.getMessage() + "）");
                }
            }
        }
        if (result.created > 0) {
            ruleCache.invalidate(projectId);
        }
        return result;
    }

    private ApiInterface buildInterface(Long projectId, String method, String path, JsonNode op) {
        ApiInterface iface = new ApiInterface();
        iface.setProjectId(projectId);
        iface.setProtocol("HTTP");
        iface.setHttpMethod(method);
        iface.setHttpPath(path);
        iface.setStatus(Constants.STATUS_ENABLED);
        JsonNode summary = op.get("summary");
        String name = summary != null && summary.isTextual()
                ? summary.asText() : "openapi-" + method + "-" + path;
        iface.setName(truncate(name, 120));
        JsonNode tags = op.get("tags");
        if (tags != null && tags.isArray() && tags.size() > 0) {
            iface.setRemark(truncate("OpenAPI: " + tags.get(0).asText(), 500));
        }
        return iface;
    }

    /** 兜底规则：优先从 200 响应的 example 生成模板骨架；无 example 用空 body。 */
    private MockRule buildFallbackRule(Long interfaceId, String path, JsonNode op) {
        MockRule rule = new MockRule();
        rule.setInterfaceId(interfaceId);
        rule.setName("openapi-fallback");
        rule.setPriority(Constants.DEFAULT_PRIORITY);
        rule.setMatchCondition("[]");
        rule.setResponseTemplate(buildTemplate(op));
        rule.setDelayType("NONE");
        rule.setFaultType("NONE");
        rule.setStatus(Constants.STATUS_ENABLED);
        return rule;
    }

    private String buildTemplate(JsonNode op) {
        JsonNode responses = op.get("responses");
        if (responses != null && responses.isObject()) {
            JsonNode ok = responses.get("200");
            if (ok == null) {
                ok = responses.get("2XX");
            }
            if (ok != null && ok.isObject()) {
                JsonNode content = ok.get("content");
                if (content != null && content.isObject()) {
                    for (Iterator<Map.Entry<String, JsonNode>> it = content.fields(); it.hasNext(); ) {
                        JsonNode media = it.next().getValue();
                        if (media != null && media.isObject() && media.has("example")) {
                            return JsonUtils.toJson(media.get("example"));
                        }
                        JsonNode schema = media == null ? null : media.get("schema");
                        if (schema != null && schema.isObject() && schema.has("example")) {
                            return JsonUtils.toJson(schema.get("example"));
                        }
                    }
                }
                // 无 example：从 schema properties 生成空值骨架
                JsonNode schema = ok.get("schema");
                if (schema == null) {
                    JsonNode content2 = ok.get("content");
                    if (content2 != null && content2.isObject() && content2.size() > 0) {
                        JsonNode media = content2.fields().next().getValue();
                        schema = media == null ? null : media.get("schema");
                    }
                }
                if (schema != null && schema.isObject() && schema.has("properties")) {
                    return JsonUtils.toJson(skeleton(schema.get("properties")));
                }
            }
        }
        return "{}";
    }

    /** 由 schema properties 生成 {"field": ""} 骨架（占位符，用户填 DSL）。 */
    private Map<String, Object> skeleton(JsonNode properties) {
        Map<String, Object> out = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> it = properties.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            JsonNode p = e.getValue();
            String type = p == null ? null : (p.get("type") == null ? null : p.get("type").asText());
            if ("object".equals(type) && p.has("properties")) {
                out.put(e.getKey(), skeleton(p.get("properties")));
            } else if ("array".equals(type)) {
                out.put(e.getKey(), new ArrayList<>());
            } else {
                out.put(e.getKey(), "");
            }
        }
        return out;
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
