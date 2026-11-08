package com.miragemock.core.cache;

import com.fasterxml.jackson.databind.JsonNode;
import com.jayway.jsonpath.JsonPath;
import com.miragemock.common.entity.MockRule;
import com.miragemock.common.model.MatchCondition;
import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 编译后的规则：实体 + 解析后的匹配条件 + 解析后的响应模板。
 *
 * <p>条件级编译缓存：regex 在加载期预编译（失败则整条规则编译失败被跳过，见 Loader）；
 * JSONPath 懒编译一次后复用（volatile/CHM 保证并发安全）。
 */
@Data
public class CompiledRule {

    /** JSONPath 编译失败哨兵：该路径视为不存在，且不重复尝试编译 */
    private static final Object INVALID_PATH = new Object();

    private MockRule entity;

    /** 已解析的匹配条件；空列表表示兜底 */
    private List<MatchCondition> conditions;

    /** 已解析的响应模板 JSON 节点 */
    private JsonNode templateNode;

    /** 条件索引 → 预编译正则（仅 op=regex 的条件，加载期编译；空表示无正则条件） */
    private Map<Integer, Pattern> regexPatterns = Collections.emptyMap();

    /** JSONPath 懒编译缓存：条件索引 → 编译结果（JsonPath 或 INVALID_PATH 哨兵） */
    private final transient ConcurrentHashMap<Integer, Object> jsonPathCache = new ConcurrentHashMap<>();

    public CompiledRule(MockRule entity, List<MatchCondition> conditions, JsonNode templateNode,
                        Map<Integer, Pattern> regexPatterns) {
        this.entity = entity;
        this.conditions = conditions;
        this.templateNode = templateNode;
        this.regexPatterns = regexPatterns == null ? Collections.emptyMap() : regexPatterns;
    }

    /** 条件索引对应的预编译正则；非 regex 条件或未预编译返回 null */
    public Pattern regexOf(int conditionIndex) {
        return regexPatterns.get(conditionIndex);
    }

    /**
     * 条件索引对应的编译后 JSONPath（懒编译，首次调用后缓存复用）。
     *
     * @return 编译后的路径；路径表达式非法返回 null（视为路径不存在，不再重复编译）
     */
    public JsonPath jsonPathOf(int conditionIndex, String pathExpr) {
        if (pathExpr == null || pathExpr.isEmpty()) {
            return null;
        }
        Object v = jsonPathCache.computeIfAbsent(conditionIndex, k -> {
            try {
                return JsonPath.compile(pathExpr);
            } catch (Exception e) {
                return INVALID_PATH;
            }
        });
        return v == INVALID_PATH ? null : (JsonPath) v;
    }

    public Long getId() {
        return entity.getId();
    }

    public String getName() {
        return entity.getName();
    }

    public Integer getPriority() {
        return entity.getPriority();
    }
}
