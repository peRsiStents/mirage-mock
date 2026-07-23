package com.miragemock.core.cache;

import com.miragemock.common.entity.ApiInterface;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 编译后的接口：实体 + 按优先级升序排列的启用规则。
 */
@Data
@AllArgsConstructor
public class CompiledInterface {

    private ApiInterface entity;

    /** 启用规则，按 priority 升序 */
    private List<CompiledRule> rules;

    public Long getId() {
        return entity.getId();
    }

    /** 协议：HTTP / TCP */
    public String getProtocol() {
        return entity.getProtocol();
    }
}
