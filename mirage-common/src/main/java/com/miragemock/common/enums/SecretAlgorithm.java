package com.miragemock.common.enums;

/**
 * 密钥算法
 */
public enum SecretAlgorithm {
    /** 国密非对称：仅用于签名/验签、加解密 */
    SM2,
    /** 国密摘要 */
    SM3,
    /** 国密对称 */
    SM4,
    /** 通用对称 */
    AES,
    /** 通用非对称 */
    RSA
}
