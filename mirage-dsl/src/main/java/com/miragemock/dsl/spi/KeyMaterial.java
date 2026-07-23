package com.miragemock.dsl.spi;

/**
 * 密钥材料：加解密/签名函数运行时所需的密钥字节。
 */
public class KeyMaterial {

    /** SM2 / SM4 / AES / RSA */
    private final String algorithm;

    /** 非对称公钥（已解码，可能为 null） */
    private final byte[] publicKey;

    /** 私钥 / 对称密钥（已解码、已解密，可能为 null） */
    private final byte[] privateKey;

    /** CBC 模式默认 IV（已解码，可能为 null） */
    private final byte[] iv;

    public KeyMaterial(String algorithm, byte[] publicKey, byte[] privateKey, byte[] iv) {
        this.algorithm = algorithm;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.iv = iv;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

    public byte[] getPrivateKey() {
        return privateKey;
    }

    public byte[] getIv() {
        return iv;
    }
}
