package com.miragemock.tcp.codec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 报文解析注册表：内置 json/key_value/fixed_fields/hex_string；custom:&lt;beanName&gt; 走 Spring 容器。
 *
 * <p>自定义协议实现 {@link MessageParser} 并注册为 Spring bean，在监听器 message_format 配置为
 * custom:&lt;beanName&gt; 即可使用。
 */
@Component
public class MessageParserRegistry {

    private static final Logger log = LoggerFactory.getLogger(MessageParserRegistry.class);

    private final Map<String, MessageParser> builtins = new HashMap<>();
    private final ApplicationContext ctx;

    @Autowired
    public MessageParserRegistry(ApplicationContext ctx, List<MessageParser> customParsers) {
        this.ctx = ctx;
        register(new JsonMessageParser());
        register(new KeyValueMessageParser());
        register(new FixedFieldsMessageParser());
        register(new HexMessageParser());
        if (customParsers != null) {
            for (MessageParser p : customParsers) {
                builtins.put(p.code(), p);
                log.info("注册自定义报文解析器: {}", p.code());
            }
        }
    }

    private void register(MessageParser parser) {
        builtins.put(parser.code(), parser);
    }

    /**
     * 按 message_format 解析。
     *
     * @param format tcp_listener.message_format，如 "json"、"custom:iso8583Parser"
     */
    public MessageParser resolve(String format) {
        if (format == null || format.isEmpty()) {
            return builtins.get("json");
        }
        if (format.startsWith("custom:")) {
            String beanName = format.substring("custom:".length());
            try {
                return ctx.getBean(beanName, MessageParser.class);
            } catch (Exception e) {
                MessageParser byCode = builtins.get(beanName);
                if (byCode != null) {
                    return byCode;
                }
                throw new IllegalArgumentException("未找到自定义报文解析器: " + beanName, e);
            }
        }
        MessageParser p = builtins.get(format);
        return p != null ? p : builtins.get("json");
    }
}
