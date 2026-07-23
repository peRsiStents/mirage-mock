package com.miragemock.tcp.codec;

import java.util.Map;

/**
 * 报文语义解析 / 序列化 SPI。实现类可打包为 jar 放入 plugins/，启动扫描注册；
 * 在 tcp_listener.message_format 配置为 custom:&lt;beanName&gt; 即可使用（如 8583、私有二进制协议）。
 */
public interface MessageParser {

    /** 格式编码，如 "json"、"key_value"；custom 实现可返回任意（按 bean 名引用） */
    String code();

    /**
     * 解析完整帧为字段 Map。
     *
     * @param frame        完整报文字节（已切分）
     * @param formatConfig message_format_config（JSON 解析后的 Map），可为空
     */
    Map<String, Object> parse(byte[] frame, Map<String, Object> formatConfig);

    /**
     * 将响应字段序列化为字节。
     *
     * @param fields       响应字段（已渲染）
     * @param formatConfig 同上
     */
    byte[] encode(Map<String, Object> fields, Map<String, Object> formatConfig);
}
