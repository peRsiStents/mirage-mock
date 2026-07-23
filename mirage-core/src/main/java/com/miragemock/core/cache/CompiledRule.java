package com.miragemock.core.cache;

import com.fasterxml.jackson.databind.JsonNode;
import com.miragemock.common.entity.MockRule;
import com.miragemock.common.model.MatchCondition;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 编译后的规则：实体 + 解析后的匹配条件 + 解析后的响应模板。
 */
@Data
@AllArgsConstructor
public class CompiledRule {

    private MockRule entity;

    /** 已解析的匹配条件；空列表表示兜底 */
    private List<MatchCondition> conditions;

    /** 已解析的响应模板 JSON 节点 */
    private JsonNode templateNode;

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
