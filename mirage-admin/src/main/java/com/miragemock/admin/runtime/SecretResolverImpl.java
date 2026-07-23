package com.miragemock.admin.runtime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.miragemock.admin.crypto.SecretCipher;
import com.miragemock.admin.mapper.SecretKeyMapper;
import com.miragemock.common.entity.SecretKey;
import com.miragemock.dsl.crypto.Codec;
import com.miragemock.dsl.spi.KeyMaterial;
import com.miragemock.dsl.spi.SecretResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 密钥别名解析器：按项目+别名取出已解密的密钥字节。
 */
@Component
public class SecretResolverImpl implements SecretResolver {

    private final SecretKeyMapper secretKeyMapper;
    private final SecretCipher cipher;

    @Autowired
    public SecretResolverImpl(SecretKeyMapper secretKeyMapper, SecretCipher cipher) {
        this.secretKeyMapper = secretKeyMapper;
        this.cipher = cipher;
    }

    @Override
    public KeyMaterial resolve(String alias, Long projectId) {
        if (alias == null || projectId == null) {
            return null;
        }
        SecretKey k = secretKeyMapper.selectOne(
                new LambdaQueryWrapper<SecretKey>()
                        .eq(SecretKey::getProjectId, projectId)
                        .eq(SecretKey::getAlias, alias));
        if (k == null) {
            return null;
        }
        byte[] pub = decodeOrNull(k.getPublicKey());
        byte[] priv = decryptKey(k.getPrivateKey());
        byte[] iv = decodeOrNull(k.getIvValue());
        return new KeyMaterial(k.getAlgorithm(), pub, priv, iv);
    }

    private byte[] decryptKey(String stored) {
        if (stored == null || stored.isEmpty()) {
            return null;
        }
        String plainB64 = cipher.decrypt(stored);
        return plainB64 == null ? null : Codec.base64Decode(plainB64);
    }

    private byte[] decodeOrNull(String b64) {
        if (b64 == null || b64.isEmpty()) {
            return null;
        }
        try {
            return Codec.base64Decode(b64);
        } catch (Exception e) {
            return null;
        }
    }
}
