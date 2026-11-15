package com.miragemock.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 接口定义（HTTP / TCP 统一抽象）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("api_interface")
public class ApiInterface extends BaseEntity {

    private Long projectId;

    private String name;

    /** HTTP / TCP */
    private String protocol;

    /** HTTP 专用：GET/POST/PUT/DELETE/ANY 等 */
    private String httpMethod;

    /** HTTP 专用：路径，支持 path 变量 /user/{id} */
    private String httpPath;

    /** TCP 专用：所属监听器 */
    private Long tcpListenerId;

    /** TCP 专用：路由匹配表达式 */
    private String tcpRouteExpr;

    /** 1 启用 / 0 停用 */
    private Integer status;

    /**
     * 录制回放模式（HTTP 专用）：0=关闭 1=录制（未命中规则时转发上游并存快照） 2=回放（优先返回录制快照）
     */
    private Integer recordMode;

    /** 录制回放上游地址（如 http://real-service:8080），未命中规则且非关闭模式时转发 */
    private String upstreamUrl;

    private String remark;
}
