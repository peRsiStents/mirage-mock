package com.miragemock.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 测试案例运行记录（proxy 模式）。createTime 即运行时间。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("test_run_log")
public class TestRunLog extends BaseEntity {

    private Long projectId;

    private Long caseId;

    /** proxy / direct */
    private String mode;

    private Integer httpStatus;

    private Long costMs;

    /** 1 通过 / 0 失败 */
    private Integer passed;

    /** JSON: 各断言结果 */
    private String assertionResult;

    private String error;

    /**
     * 请求报文 JSON：{method,url,headers,body}（HTTP）或 {target,frame,body,raw}（TCP）。
     * 用于「记录查看」——回看某次运行实际发出的请求。
     *
     * <p>{@code select = false}：列表接口不查这三列（报文可能很大，100 条会把响应撑爆），
     * 详情接口再显式 select 出来。
     */
    @TableField(select = false)
    private String requestInfo;

    /** 返回报文（响应体） */
    @TableField(select = false)
    private String responseBody;

    /** 返回报文头 JSON */
    @TableField(select = false)
    private String responseHeaders;
}
