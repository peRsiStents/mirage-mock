package com.miragemock.admin.dto;

import lombok.Data;

@Data
public class KeyRequest {

    private String alias;
    /** SM2 / SM4 / AES / RSA */
    private String algorithm;
    /** 公钥（Base64），SM2 生成场景可不传 */
    private String publicKey;
    /** 私钥/对称密钥（Base64 明文），服务端加密后落库；SM2 生成场景可不传 */
    private String privateKey;
    /** CBC 模式默认 IV（Base64），可选 */
    private String ivValue;
}
