package com.miragemock.admin.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;

/**
 * JWT 工具：签发与解析。
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long ttlMs;

    public JwtUtil(SecurityProperties props) {
        byte[] secret = normalize(props.getJwtSecret());
        this.key = Keys.hmacShaKeyFor(secret);
        this.ttlMs = props.getJwtTtlHours() * 3600_000L;
    }

    public String generate(Long userId, String username, boolean admin) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(username)
                .id(String.valueOf(userId))
                .claim("uid", userId)
                .claim("username", username)
                .claim("admin", admin)
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlMs))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private static byte[] normalize(String secret) {
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        if (raw.length >= 32) {
            return raw;
        }
        // 不足 32 字节：用 SHA-256 派生
        try {
            return MessageDigest.getInstance("SHA-256").digest(raw);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
