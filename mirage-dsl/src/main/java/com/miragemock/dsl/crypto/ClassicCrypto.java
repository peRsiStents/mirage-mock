package com.miragemock.dsl.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * 经典算法：AES（JCE）/ RSA（JCE）。SM* 见 {@link SmCrypto}。
 */
public final class ClassicCrypto {

    private ClassicCrypto() {
    }

    // ============ AES ============

    public static byte[] aesEcbEncrypt(byte[] key, byte[] data) {
        return aes("AES/ECB/PKCS5Padding", true, key, null, data);
    }

    public static byte[] aesEcbDecrypt(byte[] key, byte[] data) {
        return aes("AES/ECB/PKCS5Padding", false, key, null, data);
    }

    public static byte[] aesCbcEncrypt(byte[] key, byte[] iv, byte[] data) {
        return aes("AES/CBC/PKCS5Padding", true, key, iv, data);
    }

    public static byte[] aesCbcDecrypt(byte[] key, byte[] iv, byte[] data) {
        return aes("AES/CBC/PKCS5Padding", false, key, iv, data);
    }

    private static byte[] aes(String transformation, boolean encrypt, byte[] key, byte[] iv, byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance(transformation);
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            if (iv == null) {
                cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, keySpec);
            } else {
                cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(iv));
            }
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("AES 处理失败: " + e.getMessage(), e);
        }
    }

    // ============ RSA ============

    public static byte[] rsaEncrypt(byte[] pubX509, byte[] data) {
        try {
            PublicKey pub = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(pubX509));
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, pub);
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("RSA 加密失败: " + e.getMessage(), e);
        }
    }

    public static byte[] rsaDecrypt(byte[] privPkcs8, byte[] data) {
        try {
            PrivateKey priv = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privPkcs8));
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, priv);
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("RSA 解密失败: " + e.getMessage(), e);
        }
    }

    public static byte[] rsaSign(byte[] privPkcs8, byte[] data) {
        try {
            PrivateKey priv = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privPkcs8));
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(priv);
            sig.update(data);
            return sig.sign();
        } catch (Exception e) {
            throw new IllegalStateException("RSA 签名失败: " + e.getMessage(), e);
        }
    }

    public static boolean rsaVerify(byte[] pubX509, byte[] data, byte[] signature) {
        try {
            PublicKey pub = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(pubX509));
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(pub);
            sig.update(data);
            return sig.verify(signature);
        } catch (Exception e) {
            throw new IllegalStateException("RSA 验签失败: " + e.getMessage(), e);
        }
    }
}
