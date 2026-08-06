package com.miragemock.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 密钥管理：SM2/SM3/SM4/AES/RSA。私钥/对称密钥 AES 加密后落库。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("secret_key")
public class SecretKey extends BaseEntity {

    private Long projectId;

    /** 别名，模板中引用，如 key_merchant */
    private String alias;

    /** SM2 / SM3 / SM4 / AES / RSA */
    private String algorithm;

    /** 非对称算法公钥（Base64） */
    private String publicKey;

    /**
     * 私钥/对称密钥（AES 加密后密文，Base64）。
     * 序列化时剔除：即使将来暴露了 GET 详情接口，加密私钥也不会回前端（DB 读取/解密不受影响）。
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String privateKey;

    /** CBC 模式默认 IV（可选，Base64） */
    private String ivValue;
}
