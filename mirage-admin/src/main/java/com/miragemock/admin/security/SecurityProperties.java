package com.miragemock.admin.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 安全配置：JWT 密钥 / TTL，密钥落库主密钥。
 */
@Data
@ConfigurationProperties(prefix = "mirage.security")
public class SecurityProperties {

    /** JWT 签名密钥（≥32 字节） */
    private String jwtSecret = "mirage-mock-dev-jwt-secret-please-change-in-prod-0123456789";

    /** JWT 有效期（小时） */
    private long jwtTtlHours = 12;

    /**
     * 密钥落库主密钥（用于 AES 加密 secret_key.private_key）。
     * 生产环境务必通过环境变量 MIRAGE_MASTER_KEY 覆盖。
     */
    private String masterKey = "mirage-mock-dev-master-key";
}
