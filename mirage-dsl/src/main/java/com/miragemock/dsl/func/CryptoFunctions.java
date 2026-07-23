package com.miragemock.dsl.func;

import com.miragemock.dsl.crypto.ClassicCrypto;
import com.miragemock.dsl.crypto.Codec;
import com.miragemock.dsl.crypto.SmCrypto;
import com.miragemock.dsl.eval.ExprException;
import com.miragemock.dsl.spi.KeyMaterial;
import com.miragemock.dsl.spi.MockFunction;

import java.util.Arrays;
import java.util.List;

/**
 * 加解密 / 签名类函数：SM4 / SM2 / AES / RSA。密钥通过别名从 SecretResolver 取得。
 * 结果默认 Base64，可加参数 'hex' 改为 hex；对称算法可加 'CBC' 指定模式（默认 ECB）。
 */
public final class CryptoFunctions {

    private CryptoFunctions() {
    }

    public static List<MockFunction> all() {
        return Arrays.asList(
                MockFns.fn("sm4_encrypt", (a, c) -> {
                    KeyMaterial k = requireKey(c, FuncArgs.asString(a, 1, ""));
                    String[] opt = opts(a, 2);
                    byte[] out = "CBC".equals(opt[0])
                            ? SmCrypto.sm4CbcEncrypt(k.getPrivateKey(), ivOf(k), Codec.utf8(FuncArgs.asString(a, 0, "")))
                            : SmCrypto.sm4EcbEncrypt(k.getPrivateKey(), Codec.utf8(FuncArgs.asString(a, 0, "")));
                    return encode(out, opt[1]);
                }),
                MockFns.fn("sm4_decrypt", (a, c) -> {
                    KeyMaterial k = requireKey(c, FuncArgs.asString(a, 1, ""));
                    String[] opt = opts(a, 2);
                    byte[] cipher = decode(FuncArgs.asString(a, 0, ""), opt[1]);
                    byte[] out = "CBC".equals(opt[0])
                            ? SmCrypto.sm4CbcDecrypt(k.getPrivateKey(), ivOf(k), cipher)
                            : SmCrypto.sm4EcbDecrypt(k.getPrivateKey(), cipher);
                    return Codec.utf8(out);
                }),

                MockFns.fn("sm2_sign", (a, c) -> {
                    KeyMaterial k = requireKey(c, FuncArgs.asString(a, 1, ""));
                    String[] opt = opts(a, 2);
                    byte[] sig = SmCrypto.sign(k.getPrivateKey(), Codec.utf8(FuncArgs.asString(a, 0, "")));
                    return encode(sig, opt[1]);
                }),
                MockFns.fn("sm2_verify", (a, c) -> {
                    KeyMaterial k = requireKey(c, FuncArgs.asString(a, 2, ""));
                    String[] opt = opts(a, 3);
                    byte[] sig = decode(FuncArgs.asString(a, 1, ""), opt[1]);
                    boolean ok = SmCrypto.verify(k.getPublicKey(), Codec.utf8(FuncArgs.asString(a, 0, "")), sig);
                    return Boolean.toString(ok);
                }),
                MockFns.fn("sm2_encrypt", (a, c) -> {
                    KeyMaterial k = requireKey(c, FuncArgs.asString(a, 1, ""));
                    String[] opt = opts(a, 2);
                    return encode(SmCrypto.encrypt(k.getPublicKey(), Codec.utf8(FuncArgs.asString(a, 0, ""))), opt[1]);
                }),
                MockFns.fn("sm2_decrypt", (a, c) -> {
                    KeyMaterial k = requireKey(c, FuncArgs.asString(a, 1, ""));
                    String[] opt = opts(a, 2);
                    byte[] cipher = decode(FuncArgs.asString(a, 0, ""), opt[1]);
                    return Codec.utf8(SmCrypto.decrypt(k.getPrivateKey(), cipher));
                }),

                MockFns.fn("aes_encrypt", (a, c) -> {
                    KeyMaterial k = requireKey(c, FuncArgs.asString(a, 1, ""));
                    String[] opt = opts(a, 2);
                    byte[] out = "CBC".equals(opt[0])
                            ? ClassicCrypto.aesCbcEncrypt(k.getPrivateKey(), ivOf(k), Codec.utf8(FuncArgs.asString(a, 0, "")))
                            : ClassicCrypto.aesEcbEncrypt(k.getPrivateKey(), Codec.utf8(FuncArgs.asString(a, 0, "")));
                    return encode(out, opt[1]);
                }),
                MockFns.fn("aes_decrypt", (a, c) -> {
                    KeyMaterial k = requireKey(c, FuncArgs.asString(a, 1, ""));
                    String[] opt = opts(a, 2);
                    byte[] cipher = decode(FuncArgs.asString(a, 0, ""), opt[1]);
                    byte[] out = "CBC".equals(opt[0])
                            ? ClassicCrypto.aesCbcDecrypt(k.getPrivateKey(), ivOf(k), cipher)
                            : ClassicCrypto.aesEcbDecrypt(k.getPrivateKey(), cipher);
                    return Codec.utf8(out);
                }),

                MockFns.fn("rsa_sign", (a, c) -> {
                    KeyMaterial k = requireKey(c, FuncArgs.asString(a, 1, ""));
                    String[] opt = opts(a, 2);
                    return encode(ClassicCrypto.rsaSign(k.getPrivateKey(), Codec.utf8(FuncArgs.asString(a, 0, ""))), opt[1]);
                }),
                MockFns.fn("rsa_encrypt", (a, c) -> {
                    KeyMaterial k = requireKey(c, FuncArgs.asString(a, 1, ""));
                    String[] opt = opts(a, 2);
                    return encode(ClassicCrypto.rsaEncrypt(k.getPublicKey(), Codec.utf8(FuncArgs.asString(a, 0, ""))), opt[1]);
                })
        );
    }

    private static KeyMaterial requireKey(com.miragemock.dsl.eval.EvalContext c, String alias) {
        if (c.getSecretResolver() == null) {
            throw new ExprException("缺少 SecretResolver，无法解析密钥别名: " + alias);
        }
        KeyMaterial k = c.getSecretResolver().resolve(alias, c.getProjectId());
        if (k == null) {
            throw new ExprException("引用的密钥别名不存在: " + alias);
        }
        return k;
    }

    private static byte[] ivOf(KeyMaterial k) {
        if (k.getIv() == null) {
            throw new ExprException("CBC 模式需要 IV，但密钥 " + " 未配置 iv_value");
        }
        return k.getIv();
    }

    /** 返回 [mode, encoding]，mode 默认 ECB，encoding 默认 base64 */
    private static String[] opts(List<Object> args, int from) {
        String mode = "ECB";
        String enc = "base64";
        for (int i = from; i < args.size(); i++) {
            String s = FuncArgs.asString(args, i, "");
            if ("CBC".equalsIgnoreCase(s) || "ECB".equalsIgnoreCase(s)) {
                mode = s.toUpperCase();
            } else if ("hex".equalsIgnoreCase(s) || "base64".equalsIgnoreCase(s)) {
                enc = s.toLowerCase();
            }
        }
        return new String[]{mode, enc};
    }

    private static String encode(byte[] data, String enc) {
        return "hex".equals(enc) ? Codec.hex(data) : Codec.base64(data);
    }

    private static byte[] decode(String data, String enc) {
        return "hex".equals(enc) ? Codec.hexDecode(data) : Codec.base64Decode(data);
    }
}
