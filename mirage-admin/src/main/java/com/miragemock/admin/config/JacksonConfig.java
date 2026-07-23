package com.miragemock.admin.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Jackson 全局定制：超长整型序列化为字符串，避免前端精度丢失。
 *
 * <p>JavaScript 的 Number 为 IEEE-754 双精度，安全整数上限为 2^53-1（9007199254740991）。
 * 本系统主键采用雪花算法（{@code IdType.ASSIGN_ID}），约 2e18，远超该上限——浏览器 JSON.parse
 * 会四舍五入，导致回传给后端的 id 与库中不一致（典型现象：新建规则报「接口不存在」）。
 *
 * <p>此模块对 {@code Long}/{@code long} 做阈值判定：仅当 |value| &gt; 2^53-1 时输出为字符串，
 * 其余（毫秒时间戳约 1.7e12、状态/优先级/版本等小整数）仍按数字输出，保持 API 简洁。
 * Spring Boot 会自动把 {@link SimpleModule} Bean 注册到 web 层 ObjectMapper。
 */
@Configuration
public class JacksonConfig {

    /** JS Number.MAX_SAFE_INTEGER = 2^53 - 1 */
    private static final long JS_MAX_SAFE_INTEGER = 9007199254740991L;

    @Bean
    public SimpleModule safeLongModule() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(Long.class, SafeLongSerializer.INSTANCE);
        module.addSerializer(Long.TYPE, SafeLongSerializer.INSTANCE);
        return module;
    }

    private static final class SafeLongSerializer extends JsonSerializer<Long> {

        static final SafeLongSerializer INSTANCE = new SafeLongSerializer();

        @Override
        public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value == null) {
                gen.writeNull();
                return;
            }
            if (value > JS_MAX_SAFE_INTEGER || value < -JS_MAX_SAFE_INTEGER) {
                gen.writeString(value.toString());
            } else {
                gen.writeNumber(value.longValue());
            }
        }
    }
}
