package com.miragemock.dsl.crypto;

import org.bouncycastle.asn1.gm.GMNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.BlockCipher;
import org.bouncycastle.crypto.BufferedBlockCipher;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.crypto.engines.SM2Engine;
import org.bouncycastle.crypto.engines.SM4Engine;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithID;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.bouncycastle.crypto.params.ParametersWithRandom;
import org.bouncycastle.crypto.paddings.PKCS7Padding;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.signers.SM2Signer;
import org.bouncycastle.crypto.digests.SM3Digest;
import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;
import java.security.SecureRandom;

/**
 * 国密 SM2 / SM3 / SM4 底层实现（基于 Bouncy Castle 低级 API，仅依赖 bcprov）。
 */
public final class SmCrypto {

    private static final X9ECParameters SM2_X9 = GMNamedCurves.getByName("sm2p256v1");
    private static final ECDomainParameters SM2_SPEC =
            new ECDomainParameters(SM2_X9.getCurve(), SM2_X9.getG(), SM2_X9.getN(), SM2_X9.getH());
    private static final byte[] DEFAULT_USER_ID = Codec.utf8("1234567812345678");

    private SmCrypto() {
    }

    // ============ SM2 密钥对 ============

    public static final class Sm2KeyPair {
        public final byte[] publicKey; // 未压缩 65 字节：04||X||Y
        public final byte[] privateKey; // 32 字节

        public Sm2KeyPair(byte[] publicKey, byte[] privateKey) {
            this.publicKey = publicKey;
            this.privateKey = privateKey;
        }
    }

    public static Sm2KeyPair generateKeyPair() {
        ECKeyPairGenerator gen = new ECKeyPairGenerator();
        gen.init(new ECKeyGenerationParameters(SM2_SPEC, new SecureRandom()));
        AsymmetricCipherKeyPair kp = gen.generateKeyPair();
        ECPrivateKeyParameters priv = (ECPrivateKeyParameters) kp.getPrivate();
        ECPublicKeyParameters pub = (ECPublicKeyParameters) kp.getPublic();
        byte[] privBytes = bigIntTo32Bytes(priv.getD());
        byte[] pubBytes = pub.getQ().getEncoded(false);
        return new Sm2KeyPair(pubBytes, privBytes);
    }

    // ============ SM2 签名 / 验签 ============

    public static byte[] sign(byte[] privateKey, byte[] data) {
        ECPrivateKeyParameters priv = new ECPrivateKeyParameters(new BigInteger(1, privateKey), SM2_SPEC);
        SM2Signer signer = new SM2Signer();
        signer.init(true, new ParametersWithID(new ParametersWithRandom(priv, new SecureRandom()), DEFAULT_USER_ID));
        signer.update(data, 0, data.length);
        try {
            return signer.generateSignature();
        } catch (org.bouncycastle.crypto.CryptoException e) {
            throw new IllegalStateException("SM2 签名失败", e);
        }
    }

    public static boolean verify(byte[] publicKey, byte[] data, byte[] signature) {
        ECPublicKeyParameters pub = toPublic(publicKey);
        SM2Signer signer = new SM2Signer();
        signer.init(false, new ParametersWithID(pub, DEFAULT_USER_ID));
        signer.update(data, 0, data.length);
        return signer.verifySignature(signature);
    }

    // ============ SM2 加解密 ============

    public static byte[] encrypt(byte[] publicKey, byte[] data) {
        ECPublicKeyParameters pub = toPublic(publicKey);
        SM2Engine engine = new SM2Engine();
        engine.init(true, new ParametersWithRandom(pub, new SecureRandom()));
        try {
            return engine.processBlock(data, 0, data.length);
        } catch (org.bouncycastle.crypto.InvalidCipherTextException e) {
            throw new IllegalStateException("SM2 加密失败", e);
        }
    }

    public static byte[] decrypt(byte[] privateKey, byte[] cipher) {
        ECPrivateKeyParameters priv = new ECPrivateKeyParameters(new BigInteger(1, privateKey), SM2_SPEC);
        SM2Engine engine = new SM2Engine();
        engine.init(false, priv);
        try {
            return engine.processBlock(cipher, 0, cipher.length);
        } catch (org.bouncycastle.crypto.InvalidCipherTextException e) {
            throw new IllegalStateException("SM2 解密失败", e);
        }
    }

    // ============ SM3 ============

    public static byte[] sm3(byte[] data) {
        SM3Digest digest = new SM3Digest();
        digest.update(data, 0, data.length);
        byte[] out = new byte[digest.getDigestSize()];
        digest.doFinal(out, 0);
        return out;
    }

    // ============ SM4（ECB / CBC，PKCS7）============

    public static byte[] sm4EcbEncrypt(byte[] key, byte[] data) {
        return sm4(true, new SM4Engine(), key, null, data);
    }

    public static byte[] sm4EcbDecrypt(byte[] key, byte[] data) {
        return sm4(false, new SM4Engine(), key, null, data);
    }

    public static byte[] sm4CbcEncrypt(byte[] key, byte[] iv, byte[] data) {
        return sm4(true, new CBCBlockCipher(new SM4Engine()), key, iv, data);
    }

    public static byte[] sm4CbcDecrypt(byte[] key, byte[] iv, byte[] data) {
        return sm4(false, new CBCBlockCipher(new SM4Engine()), key, iv, data);
    }

    private static byte[] sm4(boolean encrypt, BlockCipher base, byte[] key, byte[] iv, byte[] data) {
        BufferedBlockCipher cipher = new PaddedBufferedBlockCipher(base, new PKCS7Padding());
        CipherParameters params = iv == null
                ? new KeyParameter(key)
                : new ParametersWithIV(new KeyParameter(key), iv);
        cipher.init(encrypt, params);
        byte[] out = new byte[cipher.getOutputSize(data.length)];
        int len = cipher.processBytes(data, 0, data.length, out, 0);
        try {
            len += cipher.doFinal(out, len);
        } catch (CryptoException e) {
            throw new IllegalStateException("SM4 处理失败", e);
        }
        byte[] result = new byte[len];
        System.arraycopy(out, 0, result, 0, len);
        return result;
    }

    // ============ 工具 ============

    private static ECPublicKeyParameters toPublic(byte[] publicKey) {
        ECPoint q = SM2_X9.getCurve().decodePoint(publicKey);
        return new ECPublicKeyParameters(q, SM2_SPEC);
    }

    private static byte[] bigIntTo32Bytes(BigInteger d) {
        byte[] b = d.toByteArray();
        if (b.length == 32) {
            return b;
        }
        if (b.length == 33 && b[0] == 0) {
            byte[] r = new byte[32];
            System.arraycopy(b, 1, r, 0, 32);
            return r;
        }
        byte[] r = new byte[32];
        System.arraycopy(b, 0, r, 32 - b.length, b.length);
        return r;
    }
}
