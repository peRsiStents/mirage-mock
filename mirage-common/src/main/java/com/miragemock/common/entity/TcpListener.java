package com.miragemock.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * TCP 监听配置：每个启用的监听器绑定一个端口（Netty ServerBootstrap）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tcp_listener")
public class TcpListener extends BaseEntity {

    private Long projectId;

    private String name;

    /** 监听端口 */
    private Integer port;

    /** LONG（长连接）/ SHORT（短连接） */
    private String connMode;

    /** 帧切分配置（JSON），见 §5.2 */
    private String frameConfig;

    /** 报文格式：json/xml/key_value/fixed_fields/tlv/hex_string/custom:beanName */
    private String messageFormat;

    /** 格式专属配置（JSON） */
    private String messageFormatConfig;

    /** 路由提取表达式 */
    private String routeExtract;

    /** 流水号提取表达式（异步匹配用） */
    private String serialExtract;

    /** SYNC（串行一问一答）/ ASYNC（按流水号关联） */
    private String matchMode;

    /** 主动推送配置（JSON），见 §5.5 */
    private String pushConfig;

    /** 1 启用 / 0 停用（启用即绑定端口） */
    private Integer status;
}
