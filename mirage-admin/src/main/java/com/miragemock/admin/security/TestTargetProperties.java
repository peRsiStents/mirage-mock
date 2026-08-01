package com.miragemock.admin.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

/**
 * 测试用例执行器目标地址策略（SSRF 防护）。
 *
 * <p>平台作为请求方会向用户填写的任意地址发请求（HTTP/TCP）。多用户部署下需限制可达目标，
 * 防止探测内网/云元数据。默认仅拦截云元数据等高危地址（零误伤）；如需更严格隔离，
 * 设 {@code block-private=true} 拦截私网/回环/链路本地地址，或扩充 {@code blocked-hosts}。
 */
@Data
@ConfigurationProperties(prefix = "mirage.test")
public class TestTargetProperties {

    /** true=拦截私网(10/172.16/192.168)/回环/链路本地/IPv6-ULA 地址。默认 false（不阻断测试内网/本机服务）。 */
    private boolean blockPrivate = false;

    /** 始终拦截的主机名/IP（云元数据等高危地址），按主机名(忽略大小写)或解析后 IP 匹配。 */
    private List<String> blockedHosts = Arrays.asList(
            "169.254.169.254",
            "fd00:ec2::254",
            "metadata.google.internal",
            "metadata");
}
