package com.miragemock.admin.crypto;

import com.miragemock.admin.security.SecurityProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 密钥落库主加密：AES-GCM，用于加密 secret_key.private_key。
 * 密文格式：base64( iv(12B) || ciphertext+tag )。
 */
@Component
public class SecretCipher {

    private static final int IV_LEN = 12;
    private static final int TAG_LEN_BITS = 128;
    private static final String DEFAULT_MASTER_KEY = "mirage-mock-dev-master-key";
    private static final String DEFAULT_JWT_SECRET = "mirage-mock-dev-jwt-secret-please-change-in-prod-0123456789";

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();
    private final Environment env;
    private final String masterKey;
    private final SecurityProperties props;

    public SecretCipher(SecurityProperties props, Environment env) {
        this.masterKey = props.getMasterKey();
        this.key = new SecretKeySpec(sha256(this.masterKey), "AES");
        this.env = env;
        this.props = props;
    }

    /** 生产环境强制要求高强度、非默认的主密钥与 JWT 密钥，fail-fast 避免零熵加密/签名被伪造。 */
    @PostConstruct
    public void validateSecrets() {
        boolean prod = Arrays.asList(env.getActiveProfiles()).contains("prod");
        if (!prod) {
            return;
        }
        if (masterKey == null || masterKey.length() < 32 || DEFAULT_MASTER_KEY.equals(masterKey)) {
            throw new IllegalStateException(
                    "生产环境(prod)必须通过环境变量 MIRAGE_MASTER_KEY 设置高强度主密钥(>=32 字符)且不可使用默认值");
        }
        String jwtSecret = props.getJwtSecret();
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32
                || DEFAULT_JWT_SECRET.equals(jwtSecret)) {
            throw new IllegalStateException(
                    "生产环境(prod)必须通过环境变量 MIRAGE_JWT_SECRET 设置高强度 JWT 密钥(>=32 字节)且不可使用默认值");
        }
    }

    public String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LEN_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("密钥加密失败", e);
        }
    }

    public String decrypt(String enc) {
        if (enc == null || enc.isEmpty()) {
            return null;
        }
        try {
            byte[] all = Base64.getDecoder().decode(enc);
            byte[] iv = new byte[IV_LEN];
            byte[] ct = new byte[all.length - IV_LEN];
            System.arraycopy(all, 0, iv, 0, IV_LEN);
            System.arraycopy(all, IV_LEN, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LEN_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("密钥解密失败", e);
        }
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
