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

    /** TIMEOUT 故障注入的挂起毫秒数（异步挂起，不占用容器线程） */
    private long timeoutHangMs = 60_000L;

    /** 请求体上限（字节），超过即 413，避免无鉴权端口被超大 body 打爆内存 */
    private long maxBodyBytes = 1024 * 1024L;

    /** 请求日志单字段截断长度（字符） */
    private int logTruncateLen = 8000;

    /** 是否对 Mock 响应开放 CORS（管理端 UI「试跑」跨端口直连需要） */
    private boolean corsEnabled = true;

    /** 路径匹配是否容忍末尾斜杠（/api/users 与 /api/users/ 等价） */
    private boolean trailingSlashTolerant = true;
}
