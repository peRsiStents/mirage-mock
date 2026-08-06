package com.miragemock.admin.controller;

import com.miragemock.admin.service.KeyService;
import com.miragemock.admin.service.ToolService;
import com.miragemock.common.api.Result;
import com.miragemock.common.api.ResultCode;
import com.miragemock.common.entity.SecretKey;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 工具市场：测试人员的即时小工具（格式化 + 国密/RSA 加解密签名）。
 * 所有接口无状态、不落库；密钥由请求体直接传入，或通过 keyId 引用项目密钥
 * （私钥在服务端解析、不回前端）。
 */
@RestController
@RequestMapping("/api/v1/tools")
public class ToolController {

    private final ToolService svc;
    private final KeyService keyService;

    public ToolController(ToolService svc, KeyService keyService) {
        this.svc = svc;
        this.keyService = keyService;
    }

    /** 统一包装：提取根因消息，避免把堆栈/通用 500 暴露给前端。 */
    private <T> Result<T> run(Supplier<T> supplier) {
        try {
            return Result.ok(supplier.get());
        } catch (Throwable e) {
            Throwable c = e;
            while (c.getCause() != null && c.getCause() != c) {
                c = c.getCause();
            }
            String msg = c.getMessage() != null ? c.getMessage() : e.getMessage();
            return Result.fail(ResultCode.BAD_REQUEST, msg == null ? e.toString() : msg);
        }
    }

    private String g(Map<String, String> b, String k) {
        return b.get(k);
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }

    /** 请求体携带 keyId 时，按 id 解析项目密钥（含解密私钥/对称密钥）；否则返回 null。 */
    private SecretKey resolveKey(Map<String, String> b) {
        String id = b.get("keyId");
        if (id == null || id.trim().isEmpty()) {
            return null;
        }
        try {
            return keyService.resolveForTool(Long.parseLong(id.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 引用密钥时优先用密钥里的公钥，否则用请求体里粘贴的。 */
    private String pubOr(Map<String, String> b, SecretKey k) {
        return (k != null && notEmpty(k.getPublicKey())) ? k.getPublicKey() : g(b, "pub");
    }

    /** 引用密钥时优先用密钥里解密后的私钥，否则用请求体里粘贴的。 */
    private String privOr(Map<String, String> b, SecretKey k) {
        return (k != null && notEmpty(k.getPrivateKey())) ? k.getPrivateKey() : g(b, "priv");
    }

    // ===================== JSON =====================

    @PostMapping("/json/format")
    public Result<String> jsonFormat(@RequestBody Map<String, String> b) {
        return run(() -> svc.jsonFormat(g(b, "text"), g(b, "indent")));
    }

    @PostMapping("/json/minify")
    public Result<String> jsonMinify(@RequestBody Map<String, String> b) {
        return run(() -> svc.jsonMinify(g(b, "text")));
    }

    @PostMapping("/json/validate")
    public Result<String> jsonValidate(@RequestBody Map<String, String> b) {
        return run(() -> svc.jsonValidate(g(b, "text")));
    }

    @PostMapping("/json/escape")
    public Result<String> jsonEscape(@RequestBody Map<String, String> b) {
        return run(() -> svc.jsonEscape(g(b, "text")));
    }

    @PostMapping("/json/unescape")
    public Result<String> jsonUnescape(@RequestBody Map<String, String> b) {
        return run(() -> svc.jsonUnescape(g(b, "text")));
    }

    // ===================== XML =====================

    @PostMapping("/xml/format")
    public Result<String> xmlFormat(@RequestBody Map<String, String> b) {
        return run(() -> svc.xmlFormat(g(b, "text"), parseInt(g(b, "indent"), 2)));
    }

    @PostMapping("/xml/minify")
    public Result<String> xmlMinify(@RequestBody Map<String, String> b) {
        return run(() -> svc.xmlMinify(g(b, "text")));
    }

    @PostMapping("/xml/validate")
    public Result<String> xmlValidate(@RequestBody Map<String, String> b) {
        return run(() -> svc.xmlValidate(g(b, "text")));
    }

    @PostMapping("/xml/escape")
    public Result<String> xmlEscape(@RequestBody Map<String, String> b) {
        return run(() -> svc.xmlEscape(g(b, "text")));
    }

    @PostMapping("/xml/unescape")
    public Result<String> xmlUnescape(@RequestBody Map<String, String> b) {
        return run(() -> svc.xmlUnescape(g(b, "text")));
    }

    // ===================== SQL =====================

    @PostMapping("/sql/format")
    public Result<String> sqlFormat(@RequestBody Map<String, String> b) {
        return run(() -> svc.sqlFormat(g(b, "text")));
    }

    // ===================== 编码 / 摘要 / 转换 =====================

    @PostMapping("/base64/encode")
    public Result<String> base64Encode(@RequestBody Map<String, String> b) {
        return run(() -> svc.base64Encode(g(b, "text")));
    }

    @PostMapping("/base64/decode")
    public Result<String> base64Decode(@RequestBody Map<String, String> b) {
        return run(() -> svc.base64Decode(g(b, "text")));
    }

    @PostMapping("/hex/encode")
    public Result<String> hexEncode(@RequestBody Map<String, String> b) {
        return run(() -> svc.hexEncode(g(b, "text")));
    }

    @PostMapping("/hex/decode")
    public Result<String> hexDecode(@RequestBody Map<String, String> b) {
        return run(() -> svc.hexDecode(g(b, "text")));
    }

    @PostMapping("/url/encode")
    public Result<String> urlEncode(@RequestBody Map<String, String> b) {
        return run(() -> svc.urlEncode(g(b, "text")));
    }

    @PostMapping("/url/decode")
    public Result<String> urlDecode(@RequestBody Map<String, String> b) {
        return run(() -> svc.urlDecode(g(b, "text")));
    }

    @PostMapping("/hash")
    public Result<String> hash(@RequestBody Map<String, String> b) {
        return run(() -> svc.hash(g(b, "text"), g(b, "algo")));
    }

    @PostMapping("/jwt/decode")
    public Result<Map<String, String>> jwtDecode(@RequestBody Map<String, String> b) {
        return run(() -> svc.jwtDecode(g(b, "text")));
    }

    @PostMapping("/timestamp/now")
    public Result<Map<String, Object>> timestampNow() {
        return run(svc::tsNow);
    }

    @PostMapping("/timestamp/to-epoch")
    public Result<Map<String, Object>> timestampToEpoch(@RequestBody Map<String, String> b) {
        return run(() -> svc.tsToEpoch(g(b, "text")));
    }

    @PostMapping("/timestamp/from-epoch")
    public Result<Map<String, Object>> timestampFromEpoch(@RequestBody Map<String, String> b) {
        return run(() -> svc.tsFromEpoch(g(b, "text")));
    }

    @PostMapping("/uuid")
    public Result<String> uuid(@RequestBody Map<String, String> b) {
        return run(() -> svc.uuid(parseInt(g(b, "count"), 1)));
    }

    // ===================== SM3 =====================

    @PostMapping("/sm3")
    public Result<String> sm3(@RequestBody Map<String, String> b) {
        return run(() -> svc.sm3(g(b, "text"), g(b, "outEnc")));
    }

    // ===================== SM4 =====================

    @PostMapping("/sm4/encrypt")
    public Result<String> sm4Encrypt(@RequestBody Map<String, String> b) {
        SecretKey k = resolveKey(b);
        String key, iv, keyEnc;
        if (k != null && notEmpty(k.getPrivateKey())) {
            // 引用项目密钥：对称密钥/IV 落库为 Base64，固定按 Base64 解码
            key = k.getPrivateKey();
            iv = notEmpty(k.getIvValue()) ? k.getIvValue() : g(b, "iv");
            keyEnc = "base64";
        } else {
            key = g(b, "key"); iv = g(b, "iv"); keyEnc = g(b, "keyEnc");
        }
        final String fKey = key, fIv = iv, fKeyEnc = keyEnc;
        return run(() -> svc.sm4Encrypt(g(b, "text"), fKey, fIv, g(b, "mode"), fKeyEnc, g(b, "outEnc")));
    }

    @PostMapping("/sm4/decrypt")
    public Result<String> sm4Decrypt(@RequestBody Map<String, String> b) {
        SecretKey k = resolveKey(b);
        String key, iv, keyEnc;
        if (k != null && notEmpty(k.getPrivateKey())) {
            key = k.getPrivateKey();
            iv = notEmpty(k.getIvValue()) ? k.getIvValue() : g(b, "iv");
            keyEnc = "base64";
        } else {
            key = g(b, "key"); iv = g(b, "iv"); keyEnc = g(b, "keyEnc");
        }
        final String fKey = key, fIv = iv, fKeyEnc = keyEnc;
        return run(() -> svc.sm4Decrypt(g(b, "text"), fKey, fIv, g(b, "mode"), fKeyEnc, g(b, "inEnc")));
    }

    @PostMapping("/sm4/key")
    public Result<String> sm4Key() {
        return run(svc::sm4GenKey);
    }

    // ===================== SM2 =====================

    @PostMapping("/sm2/encrypt")
    public Result<String> sm2Encrypt(@RequestBody Map<String, String> b) {
        SecretKey k = resolveKey(b);
        String pub = pubOr(b, k);
        return run(() -> svc.sm2Encrypt(g(b, "text"), pub, g(b, "outEnc")));
    }

    @PostMapping("/sm2/decrypt")
    public Result<String> sm2Decrypt(@RequestBody Map<String, String> b) {
        SecretKey k = resolveKey(b);
        String priv = privOr(b, k);
        return run(() -> svc.sm2Decrypt(g(b, "text"), priv, g(b, "inEnc")));
    }

    @PostMapping("/sm2/sign")
    public Result<String> sm2Sign(@RequestBody Map<String, String> b) {
        SecretKey k = resolveKey(b);
        String priv = privOr(b, k);
        return run(() -> svc.sm2Sign(g(b, "text"), priv, g(b, "outEnc")));
    }

    @PostMapping("/sm2/verify")
    public Result<Boolean> sm2Verify(@RequestBody Map<String, String> b) {
        SecretKey k = resolveKey(b);
        String pub = pubOr(b, k);
        return run(() -> svc.sm2Verify(g(b, "text"), g(b, "sig"), pub, g(b, "inEnc")));
    }

    @PostMapping("/sm2/keypair")
    public Result<Map<String, String>> sm2Keypair() {
        return run(svc::sm2Gen);
    }

    // ===================== RSA =====================

    @PostMapping("/rsa/encrypt")
    public Result<String> rsaEncrypt(@RequestBody Map<String, String> b) {
        SecretKey k = resolveKey(b);
        String pub = pubOr(b, k);
        return run(() -> svc.rsaEncrypt(g(b, "text"), pub));
    }

    @PostMapping("/rsa/decrypt")
    public Result<String> rsaDecrypt(@RequestBody Map<String, String> b) {
        SecretKey k = resolveKey(b);
        String priv = privOr(b, k);
        return run(() -> svc.rsaDecrypt(g(b, "text"), priv));
    }

    @PostMapping("/rsa/sign")
    public Result<String> rsaSign(@RequestBody Map<String, String> b) {
        SecretKey k = resolveKey(b);
        String priv = privOr(b, k);
        return run(() -> svc.rsaSign(g(b, "text"), priv));
    }

    @PostMapping("/rsa/verify")
    public Result<Boolean> rsaVerify(@RequestBody Map<String, String> b) {
        SecretKey k = resolveKey(b);
        String pub = pubOr(b, k);
        return run(() -> svc.rsaVerify(g(b, "text"), g(b, "sig"), pub));
    }

    @PostMapping("/rsa/keypair")
    public Result<Map<String, String>> rsaKeypair(@RequestBody Map<String, String> b) {
        return run(() -> svc.rsaGen(parseInt(g(b, "bits"), 2048)));
    }

    private int parseInt(String s, int def) {
        if (s == null || s.isEmpty()) {
            return def;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
