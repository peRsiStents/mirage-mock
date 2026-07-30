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

    /**
     * 请求体类型：none / form-data / x-www-form-urlencoded / raw / binary（旧值 json/form/raw 后端兼容）。
     * body 语义随类型变化：raw=原始文本；x-www/form-data=JSON 行数组；binary=JSON{fileName,contentType,dataB64}。
     */
    private String bodyType;

    private String body;

    /** raw / binary 的显式 Content-Type（如 application/json、application/octet-stream）；结构化类型忽略。 */
    private String bodyContentType;

    /** JSON: [{type,target,op,expected}] */
    private String assertions;

    /** 数据驱动：JSON 数组 [{k:v,...}]，每行注入 ${var.<k>} 逐行运行；空=普通单跑 */
    private String dataSet;

    /** proxy(后端转发) / direct(浏览器直发)，UI 默认模式 */
    private String mode;

    private Integer status;

    private String remark;
}
