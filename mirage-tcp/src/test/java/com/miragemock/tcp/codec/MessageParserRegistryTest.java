package com.miragemock.tcp.codec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 报文解析注册表测试：格式名拼错必须显式报错，不能静默回退 JSON。
 */
class MessageParserRegistryTest {

    private MessageParserRegistry registry() {
        return new MessageParserRegistry(null, new ArrayList<>());
    }

    @Test
    @DisplayName("内置格式可解析")
    void builtinsResolve() {
        MessageParserRegistry reg = registry();
        assertEquals("json", reg.resolve("json").code());
        assertEquals("key_value", reg.resolve("key_value").code());
        assertEquals("fixed_fields", reg.resolve("fixed_fields").code());
        assertEquals("hex_string", reg.resolve("hex_string").code());
    }

    @Test
    @DisplayName("空格式名默认 JSON")
    void emptyFormatDefaultsToJson() {
        assertEquals("json", registry().resolve(null).code());
        assertEquals("json", registry().resolve("").code());
    }

    @Test
    @DisplayName("未知格式：抛 IllegalArgumentException 并给出可用格式列表")
    void unknownFormatThrows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> registry().resolve("jsn"));
        assertNotNull(e.getMessage());
        assertTrue(e.getMessage().contains("jsn"));
        assertTrue(e.getMessage().contains("json"));
    }

    @Test
    @DisplayName("custom: 前缀但 bean 不存在：抛异常")
    void unknownCustomThrows() {
        assertThrows(IllegalArgumentException.class, () -> registry().resolve("custom:nope"));
    }
}
