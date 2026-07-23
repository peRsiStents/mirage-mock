package com.miragemock.dsl.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmCryptoTest {

    @Test
    void sm3_knownValue() {
        // SM3("abc") = 66c7f0f4... (GB/T 32905-2016 标准示例)
        byte[] digest = SmCrypto.sm3(Codec.utf8("abc"));
        assertEquals("66c7f0f462eeedd9d1f2d46bdc10e4e24167c4875cf2f7a2297da02b8f4ba8e0", Codec.hex(digest));
    }

    @Test
    void sm2_signVerify_roundtrip() {
        SmCrypto.Sm2KeyPair kp = SmCrypto.generateKeyPair();
        byte[] data = Codec.utf8("hello mirage");
        byte[] sig = SmCrypto.sign(kp.privateKey, data);
        assertTrue(SmCrypto.verify(kp.publicKey, data, sig), "SM2 验签应通过");
    }

    @Test
    void sm2_encryptDecrypt_roundtrip() {
        SmCrypto.Sm2KeyPair kp = SmCrypto.generateKeyPair();
        byte[] data = Codec.utf8("secret payload");
        byte[] cipher = SmCrypto.encrypt(kp.publicKey, data);
        byte[] plain = SmCrypto.decrypt(kp.privateKey, cipher);
        assertArrayEquals(data, plain);
    }

    @Test
    void sm4_ecb_roundtrip() {
        byte[] key = Codec.utf8("1234567890abcdef");
        byte[] data = Codec.utf8("plain text to encrypt");
        byte[] cipher = SmCrypto.sm4EcbEncrypt(key, data);
        byte[] plain = SmCrypto.sm4EcbDecrypt(key, cipher);
        assertArrayEquals(data, plain);
    }

    @Test
    void sm4_cbc_roundtrip() {
        byte[] key = Codec.utf8("1234567890abcdef");
        byte[] iv = Codec.utf8("abcdef0123456789");
        byte[] data = Codec.utf8("plain text to encrypt");
        byte[] cipher = SmCrypto.sm4CbcEncrypt(key, iv, data);
        byte[] plain = SmCrypto.sm4CbcDecrypt(key, iv, cipher);
        assertArrayEquals(data, plain);
    }
}
