package com.miragemock.admin.dto;

import lombok.Data;

import java.util.List;

/**
 * 测试用例批量操作请求：批量运行(ids+envId) / 批量改状态(ids+status) 复用。
 */
@Data
public class BatchCaseRequest {

    /** 目标用例 id 列表 */
    private List<Long> ids;

    /** 运行环境（批量运行时可选，注入 ${var.*} 与 baseUrl） */
    private Long envId;

    /** 目标状态：1=启用 0=停用（批量改状态用） */
    private Integer status;
}
