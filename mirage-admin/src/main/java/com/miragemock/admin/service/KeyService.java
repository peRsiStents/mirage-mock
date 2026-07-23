package com.miragemock.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.miragemock.admin.crypto.SecretCipher;
import com.miragemock.admin.dto.KeyRequest;
import com.miragemock.admin.dto.Sm2KeyResult;
import com.miragemock.admin.mapper.SecretKeyMapper;
import com.miragemock.common.api.ResultCode;
import com.miragemock.common.entity.SecretKey;
import com.miragemock.common.exception.BizException;
import com.miragemock.dsl.crypto.Codec;
import com.miragemock.dsl.crypto.SmCrypto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KeyService {

    private final SecretKeyMapper keyMapper;
    private final SecretCipher cipher;

    @Autowired
    public KeyService(SecretKeyMapper keyMapper, SecretCipher cipher) {
        this.keyMapper = keyMapper;
        this.cipher = cipher;
    }

    public List<SecretKey> list(Long projectId) {
        List<SecretKey> keys = keyMapper.selectList(
                new LambdaQueryWrapper<SecretKey>().eq(SecretKey::getProjectId, projectId));
        for (SecretKey k : keys) {
            k.setPrivateKey(null); // 列表不回显私钥
        }
        return keys;
    }

    public SecretKey get(Long id) {
        SecretKey k = keyMapper.selectById(id);
        if (k == null) {
            throw new BizException(ResultCode.KEY_NOT_FOUND);
        }
        return k;
    }

    public SecretKey create(Long projectId, KeyRequest req) {
        Long count = keyMapper.selectCount(new LambdaQueryWrapper<SecretKey>()
                .eq(SecretKey::getProjectId, projectId)
                .eq(SecretKey::getAlias, req.getAlias()));
        if (count != null && count > 0) {
            throw new BizException(ResultCode.KEY_ALIAS_EXISTS);
        }
        SecretKey k = new SecretKey();
        k.setProjectId(projectId);
        k.setAlias(req.getAlias());
        k.setAlgorithm(req.getAlgorithm() == null ? "SM4" : req.getAlgorithm());
        k.setPublicKey(req.getPublicKey());
        if (req.getPrivateKey() != null && !req.getPrivateKey().isEmpty()) {
            k.setPrivateKey(cipher.encrypt(req.getPrivateKey()));
        }
        k.setIvValue(req.getIvValue());
        keyMapper.insert(k);
        return mask(k);
    }

    public void delete(Long id) {
        get(id);
        keyMapper.deleteById(id);
    }

    /**
     * 服务端生成 SM2 密钥对，私钥加密落库，生成时明文返回一次。
     */
    public Sm2KeyResult generateSm2(Long projectId, String alias) {
        if (alias == null || alias.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "alias 不能为空");
        }
        Long count = keyMapper.selectCount(new LambdaQueryWrapper<SecretKey>()
                .eq(SecretKey::getProjectId, projectId)
                .eq(SecretKey::getAlias, alias));
        if (count != null && count > 0) {
            throw new BizException(ResultCode.KEY_ALIAS_EXISTS);
        }
        SmCrypto.Sm2KeyPair kp = SmCrypto.generateKeyPair();
        String pubB64 = Codec.base64(kp.publicKey);
        String privB64 = Codec.base64(kp.privateKey);

        SecretKey k = new SecretKey();
        k.setProjectId(projectId);
        k.setAlias(alias);
        k.setAlgorithm("SM2");
        k.setPublicKey(pubB64);
        k.setPrivateKey(cipher.encrypt(privB64));
        keyMapper.insert(k);
        return new Sm2KeyResult(alias, "SM2", pubB64, privB64);
    }

    private SecretKey mask(SecretKey k) {
        k.setPrivateKey(null);
        return k;
    }
}
