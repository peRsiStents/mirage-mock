package com.miragemock.http;

import com.miragemock.common.constant.Constants;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * HTTP Mock 服务配置。
 */
@Data
@ConfigurationProperties(prefix = "mirage.http")
public class MirageHttpProperties {

    /** 是否启用 HTTP Mock 端口 */
    private boolean enabled = true;

    /** Mock 流量监听端口 */
    private int port = Constants.DEFAULT_MOCK_HTTP_PORT;

    /** TIMEOUT 故障注入的挂起毫秒数（保护容器线程） */
    private long timeoutHangMs = 60_000L;

    /** Mock 请求体最大字节数（防超大 body 打爆内存），默认 1MB */
    private int maxBodyBytes = 1_048_576;
}
