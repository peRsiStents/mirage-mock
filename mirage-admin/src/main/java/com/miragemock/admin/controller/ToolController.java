package com.miragemock.admin.controller;

import com.miragemock.admin.service.ToolService;
import com.miragemock.common.api.Result;
import com.miragemock.common.api.ResultCode;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 工具市场：测试人员的即时小工具（格式化 + 国密/RSA 加解密签名）。
 * 所有接口无状态、不落库；密钥由请求体直接传入。
 */
@RestController
@RequestMapping("/api/v1/tools")
public class ToolController {

    private final ToolService svc;

    public ToolController(ToolService svc) {
        this.svc = svc;
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

    // ===================== SM3 =====================

    @PostMapping("/sm3")
    public Result<String> sm3(@RequestBody Map<String, String> b) {
        return run(() -> svc.sm3(g(b, "text"), g(b, "outEnc")));
    }

    // ===================== SM4 =====================

    @PostMapping("/sm4/encrypt")
    public Result<String> sm4Encrypt(@RequestBody Map<String, String> b) {
        return run(() -> svc.sm4Encrypt(g(b, "text"), g(b, "key"), g(b, "iv"),
                g(b, "mode"), g(b, "keyEnc"), g(b, "outEnc")));
    }

    @PostMapping("/sm4/decrypt")
    public Result<String> sm4Decrypt(@RequestBody Map<String, String> b) {
        return run(() -> svc.sm4Decrypt(g(b, "text"), g(b, "key"), g(b, "iv"),
                g(b, "mode"), g(b, "keyEnc"), g(b, "inEnc")));
    }

    @PostMapping("/sm4/key")
    public Result<String> sm4Key() {
        return run(svc::sm4GenKey);
    }

    // ===================== SM2 =====================

    @PostMapping("/sm2/encrypt")
    public Result<String> sm2Encrypt(@RequestBody Map<String, String> b) {
        return run(() -> svc.sm2Encrypt(g(b, "text"), g(b, "pub"), g(b, "outEnc")));
    }

    @PostMapping("/sm2/decrypt")
    public Result<String> sm2Decrypt(@RequestBody Map<String, String> b) {
        return run(() -> svc.sm2Decrypt(g(b, "text"), g(b, "priv"), g(b, "inEnc")));
    }

    @PostMapping("/sm2/sign")
    public Result<String> sm2Sign(@RequestBody Map<String, String> b) {
        return run(() -> svc.sm2Sign(g(b, "text"), g(b, "priv"), g(b, "outEnc")));
    }

    @PostMapping("/sm2/verify")
    public Result<Boolean> sm2Verify(@RequestBody Map<String, String> b) {
        return run(() -> svc.sm2Verify(g(b, "text"), g(b, "sig"), g(b, "pub"), g(b, "inEnc")));
    }

    @PostMapping("/sm2/keypair")
    public Result<Map<String, String>> sm2Keypair() {
        return run(svc::sm2Gen);
    }

    // ===================== RSA =====================

    @PostMapping("/rsa/encrypt")
    public Result<String> rsaEncrypt(@RequestBody Map<String, String> b) {
        return run(() -> svc.rsaEncrypt(g(b, "text"), g(b, "pub")));
    }

    @PostMapping("/rsa/decrypt")
    public Result<String> rsaDecrypt(@RequestBody Map<String, String> b) {
        return run(() -> svc.rsaDecrypt(g(b, "text"), g(b, "priv")));
    }

    @PostMapping("/rsa/sign")
    public Result<String> rsaSign(@RequestBody Map<String, String> b) {
        return run(() -> svc.rsaSign(g(b, "text"), g(b, "priv")));
    }

    @PostMapping("/rsa/verify")
    public Result<Boolean> rsaVerify(@RequestBody Map<String, String> b) {
        return run(() -> svc.rsaVerify(g(b, "text"), g(b, "sig"), g(b, "pub")));
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
