package com.miragemock.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 请求日志。rule_id 为空表示未命中。
 */
@Data
@TableName("mock_request_log")
public class MockRequestLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long projectId;

    private Long interfaceId;

    private Long ruleId;

    private String protocol;

    private String clientAddr;

    /** 请求原文（HTTP 报文摘要或 TCP Hex） */
    private String requestRaw;

    /** 解析后的字段 JSON */
    private String requestParsed;

    /** 实际响应原文 */
    private String responseRaw;

    /** 1 命中 / 0 未命中 */
    private Integer matched;

    private Integer costMs;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
