package com.miragemock.core.cache;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 项目快照：一个项目的全部启用接口与规则。
 */
@Data
@AllArgsConstructor
public class ProjectSnapshot {

    private Long projectId;

    private String projectCode;

    private List<CompiledInterface> interfaces;

    /** 该项目的 HTTP 接口 */
    public List<CompiledInterface> httpInterfaces() {
        java.util.List<CompiledInterface> list = new java.util.ArrayList<>();
        if (interfaces != null) {
            for (CompiledInterface itf : interfaces) {
                if ("HTTP".equalsIgnoreCase(itf.getProtocol())) {
                    list.add(itf);
                }
            }
        }
        return list;
    }
}
