package com.miragemock.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 测试案例：平台作为请求方调用三方 HTTP 服务（Postman 风格）。
 * headers / query / assertions 以 JSON 文本存储。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("test_case")
public class TestCase extends BaseEntity {

    private Long projectId;

    private String name;

    /** GET/POST/PUT/DELETE/PATCH/HEAD/OPTIONS */
    private String method;

    private String url;

    /** JSON: [{k,v}] */
    private String headers;

    /** JSON: [{k,v}] URL 查询参数 */
    private String query;

    /** none / json / form / raw */
    private String bodyType;

    private String body;

    /** JSON: [{type,target,op,expected}] */
    private String assertions;

    /** proxy(后端转发) / direct(浏览器直发)，UI 默认模式 */
    private String mode;

    private Integer status;

    private String remark;
}
