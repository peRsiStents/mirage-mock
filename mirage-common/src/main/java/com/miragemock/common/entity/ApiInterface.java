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

    private String remark;
}
