package com.miragemock.tcp.codec;

import com.miragemock.dsl.crypto.Codec;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hex 透传：不解析，整体 hex 字符串放入字段 _hex，模板内用 hex 函数处理。编码时取 _hex 字段。
 */
public class HexMessageParser implements MessageParser {

    @Override
    public String code() {
        return "hex_string";
    }

    @Override
    public Map<String, Object> parse(byte[] frame, Map<String, Object> formatConfig) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("_hex", Codec.hex(frame));
        map.put("_len", frame.length);
        return map;
    }

    @Override
    public byte[] encode(Map<String, Object> fields, Map<String, Object> formatConfig) {
        Object hex = fields.get("_hex");
        if (hex == null) {
            // 无 _hex 字段，退化为将字段 JSON 以 UTF-8 字节返回
            return new byte[0];
        }
        return Codec.hexDecode(hex.toString());
    }
}
