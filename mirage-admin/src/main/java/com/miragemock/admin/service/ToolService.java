package com.miragemock.admin.service;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miragemock.dsl.crypto.ClassicCrypto;
import com.miragemock.dsl.crypto.Codec;
import com.miragemock.dsl.crypto.SmCrypto;
import com.miragemock.common.util.JsonUtils;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.UnmappableCharacterException;
import java.security.InvalidKeyException;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工具市场：面向测试人员的即时小工具。
 *
 * <p>无状态、不落库（内存 H2 重启不影响）。加解密直接复用 {@code mirage-dsl} 的
 * {@link SmCrypto}（SM2/SM3/SM4）与 {@link ClassicCrypto}（RSA/AES），与 Mock 引擎、
 * DSL 函数为同一套实现，保证「函数市场里写的表达式」与「工具里手算的结果」一致。
 * 密钥由用户直接粘贴（Base64/Hex/文本），不走项目密钥别名——这是一个临时计算工具。
 *
 * <p>格式化：JSON 用全新 {@link ObjectMapper}（避免全局 NON_NULL/长整型定制干扰）；
 * XML 用 JDK 自带 DOM（禁用外部实体防 XXE）；SQL 为自研关键字级轻量格式化器。
 */
@Service
public class ToolService {

    /** JSON 工具专用、无任何全局定制的 mapper，保证忠实保留结构。 */
    private static final ObjectMapper JSON = new ObjectMapper();

    // ===================== JSON =====================

    public String jsonFormat(String text, String indent) {
        JsonNode node = parseJson(text);
        try {
            DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
            String ind = "tab".equals(indent) ? "\t" : ("4".equals(indent) ? "    " : "  ");
            DefaultIndenter indenter = new DefaultIndenter(ind, "\n");
            pp.indentObjectsWith(indenter);
            pp.indentArraysWith(indenter);
            return JSON.writer(pp).writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 格式化失败: " + e.getMessage(), e);
        }
    }

    public String jsonMinify(String text) {
        JsonNode node = parseJson(text);
        try {
            return JSON.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 压缩失败: " + e.getMessage(), e);
        }
    }

    public String jsonValidate(String text) {
        parseJson(text);
        return "JSON 语法合法 ✓";
    }

    /** 将任意文本转为合法 JSON 字符串字面量（含两侧引号、转义），便于嵌入代码。 */
    public String jsonEscape(String text) {
        try {
            return JSON.writeValueAsString(text == null ? "" : text);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 转义失败: " + e.getMessage(), e);
        }
    }

    public String jsonUnescape(String text) {
        String s = text == null ? "" : text.trim();
        if (s.isEmpty()) {
            return "";
        }
        String body = s;
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            body = s;
        }
        try {
            return JSON.readValue(body, String.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("不是合法的 JSON 字符串字面量: " + e.getMessage());
        }
    }

    private JsonNode parseJson(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("输入不能为空");
        }
        try {
            return JSON.readTree(text);
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON 解析失败: " + rootMessage(e));
        }
    }

    // ===================== XML =====================

    public String xmlFormat(String text, int indentAmount) {
        Document doc = parseXml(text);
        removeBlankText(doc.getDocumentElement());
        return transform(doc, true, indentAmount);
    }

    public String xmlMinify(String text) {
        Document doc = parseXml(text);
        removeBlankText(doc.getDocumentElement());
        String raw = transform(doc, false, 0);
        return raw.replaceAll(">\\s+<", "><").trim();
    }

    public String xmlValidate(String text) {
        parseXml(text);
        return "XML 语法合法 ✓";
    }

    public String xmlEscape(String text) {
        String s = text == null ? "" : text;
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    public String xmlUnescape(String text) {
        String s = text == null ? "" : text;
        return s.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&");
    }

    private Document parseXml(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("输入不能为空");
        }
        try {
            DocumentBuilderFactory dbf = secureDbf();
            DocumentBuilder db = dbf.newDocumentBuilder();
            return db.parse(new InputSource(new StringReader(text)));
        } catch (Exception e) {
            throw new IllegalArgumentException("XML 解析失败: " + rootMessage(e));
        }
    }

    private DocumentBuilderFactory secureDbf() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        // 禁用外部实体/外部 DTD，防 XXE（工具处理任意用户 XML）
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        return dbf;
    }

    private void removeBlankText(Node node) {
        if (node == null) {
            return;
        }
        NodeList children = node.getChildNodes();
        for (int i = children.getLength() - 1; i >= 0; i--) {
            Node c = children.item(i);
            if (c.getNodeType() == Node.TEXT_NODE && c.getTextContent().trim().isEmpty()) {
                node.removeChild(c);
            } else if (c.getNodeType() == Node.ELEMENT_NODE) {
                removeBlankText(c);
            }
        }
    }

    private String transform(Document doc, boolean indent, int indentAmount) {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Transformer t = tf.newTransformer();
            t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            if (indent) {
                t.setOutputProperty(OutputKeys.INDENT, "yes");
                t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount",
                        String.valueOf(indentAmount <= 0 ? 2 : indentAmount));
            } else {
                t.setOutputProperty(OutputKeys.INDENT, "no");
            }
            StringWriter sw = new StringWriter();
            t.transform(new DOMSource(doc), new StreamResult(sw));
            return sw.toString();
        } catch (Exception e) {
            throw new IllegalStateException("XML 序列化失败: " + rootMessage(e), e);
        }
    }

    // ===================== SQL =====================

    private static final List<String> SQL_CLAUSES = Arrays.asList(
            "SELECT", "DISTINCT", "FROM", "WHERE", "AND", "OR",
            "GROUP BY", "HAVING", "ORDER BY", "LIMIT", "OFFSET",
            "UNION ALL", "UNION", "INTERSECT", "EXCEPT",
            "LEFT OUTER JOIN", "RIGHT OUTER JOIN", "FULL OUTER JOIN",
            "LEFT JOIN", "RIGHT JOIN", "INNER JOIN", "OUTER JOIN", "CROSS JOIN", "NATURAL JOIN",
            "JOIN", "ON", "USING",
            "INSERT INTO", "VALUES", "UPDATE", "SET", "DELETE FROM",
            "CREATE TABLE", "ALTER TABLE", "DROP TABLE", "TRUNCATE TABLE");

    public String sqlFormat(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return "";
        }
        // 去注释 + 折叠空白（SQL 字面量内换行极少，工具场景可接受）
        String s = sql.replaceAll("/\\*.*?\\*/", " ")
                .replaceAll("--[^\\n]*", " ")
                .replaceAll("\\s+", " ")
                .trim();

        List<String> kw = new ArrayList<>(SQL_CLAUSES);
        kw.sort((a, b) -> Integer.compare(b.length(), a.length()));
        StringBuilder alt = new StringBuilder();
        for (int i = 0; i < kw.size(); i++) {
            if (i > 0) {
                alt.append('|');
            }
            alt.append(Pattern.quote(kw.get(i)));
        }
        Pattern p = Pattern.compile("\\b(" + alt + ")\\b", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(s);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (m.find()) {
            out.append(s, last, m.start());
            String k = m.group(1).toUpperCase(Locale.ROOT);
            boolean indentChild = k.equals("AND") || k.equals("OR");
            if (out.length() > 0 || last > 0) {
                out.append("\n").append(indentChild ? "  " : "");
            }
            out.append(k);
            last = m.end();
        }
        out.append(s, last, s.length());
        return out.toString().replaceAll(" +\\n", "\n").trim();
    }

    // ===================== SM3 =====================

    public String sm3(String text, String outEnc) {
        byte[] d = SmCrypto.sm3(Codec.utf8(text == null ? "" : text));
        return encode(d, outEnc);
    }

    // ===================== SM4 =====================

    public String sm4Encrypt(String text, String key, String iv, String mode, String keyEnc, String outEnc) {
        byte[] k = decodeKey(key, keyEnc, "SM4");
        if (k.length != 16) {
            throw new IllegalArgumentException("SM4 密钥必须为 16 字节（128 位），当前 " + k.length + " 字节");
        }
        byte[] data = Codec.utf8(text == null ? "" : text);
        byte[] out = "CBC".equalsIgnoreCase(mode)
                ? SmCrypto.sm4CbcEncrypt(k, ivBytes(iv, keyEnc), data)
                : SmCrypto.sm4EcbEncrypt(k, data);
        return encode(out, outEnc);
    }

    public String sm4Decrypt(String text, String key, String iv, String mode, String keyEnc, String inEnc) {
        byte[] k = decodeKey(key, keyEnc, "SM4");
        if (k.length != 16) {
            throw new IllegalArgumentException("SM4 密钥必须为 16 字节（128 位），当前 " + k.length + " 字节");
        }
        byte[] cipher = decode(text, inEnc);
        byte[] out = "CBC".equalsIgnoreCase(mode)
                ? SmCrypto.sm4CbcDecrypt(k, ivBytes(iv, keyEnc), cipher)
                : SmCrypto.sm4EcbDecrypt(k, cipher);
        return Codec.utf8(out);
    }

    public String sm4GenKey() {
        byte[] k = new byte[16];
        new SecureRandom().nextBytes(k);
        return Codec.hex(k);
    }

    // ===================== SM2 =====================

    public String sm2Encrypt(String text, String pubKey, String outEnc) {
        byte[] c = SmCrypto.encrypt(sm2Key(pubKey, "公钥"), Codec.utf8(text == null ? "" : text));
        return encode(c, outEnc);
    }

    public String sm2Decrypt(String text, String privKey, String inEnc) {
        byte[] plain = SmCrypto.decrypt(sm2Key(privKey, "私钥"), decode(text, inEnc));
        return Codec.utf8(plain);
    }

    public String sm2Sign(String text, String privKey, String outEnc) {
        byte[] sig = SmCrypto.sign(sm2Key(privKey, "私钥"), Codec.utf8(text == null ? "" : text));
        return encode(sig, outEnc);
    }

    public boolean sm2Verify(String text, String sig, String pubKey, String inEnc) {
        return SmCrypto.verify(sm2Key(pubKey, "公钥"), Codec.utf8(text == null ? "" : text), decode(sig, inEnc));
    }

    public Map<String, String> sm2Gen() {
        SmCrypto.Sm2KeyPair kp = SmCrypto.generateKeyPair();
        Map<String, String> m = new LinkedHashMap<>();
        m.put("publicKey", Codec.base64(kp.publicKey));
        m.put("privateKey", Codec.base64(kp.privateKey));
        return m;
    }

    private byte[] sm2Key(String key, String role) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("SM2 " + role + "不能为空");
        }
        try {
            return Codec.base64Decode(key.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("SM2 " + role + "应为 Base64：" + e.getMessage());
        }
    }

    // ===================== RSA =====================

    public String rsaEncrypt(String text, String pubKey) {
        return Codec.base64(ClassicCrypto.rsaEncrypt(rsaKey(pubKey, "公钥"), Codec.utf8(text == null ? "" : text)));
    }

    public String rsaDecrypt(String text, String privKey) {
        return Codec.utf8(ClassicCrypto.rsaDecrypt(rsaKey(privKey, "私钥"), Codec.base64Decode(text == null ? "" : text)));
    }

    public String rsaSign(String text, String privKey) {
        return Codec.base64(ClassicCrypto.rsaSign(rsaKey(privKey, "私钥"), Codec.utf8(text == null ? "" : text)));
    }

    public boolean rsaVerify(String text, String sig, String pubKey) {
        return ClassicCrypto.rsaVerify(rsaKey(pubKey, "公钥"), Codec.utf8(text == null ? "" : text), Codec.base64Decode(sig == null ? "" : sig));
    }

    public Map<String, String> rsaGen(int bits) {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(bits <= 0 ? 2048 : Math.min(bits, 4096));
            java.security.KeyPair kp = gen.generateKeyPair();
            Map<String, String> m = new LinkedHashMap<>();
            m.put("publicKey", Codec.base64(kp.getPublic().getEncoded()));
            m.put("privateKey", Codec.base64(kp.getPrivate().getEncoded()));
            return m;
        } catch (Exception e) {
            throw new IllegalStateException("RSA 密钥生成失败: " + e.getMessage(), e);
        }
    }

    /** 接受 PEM（去头尾/换行）或裸 Base64（X509 公钥 / PKCS8 私钥）。 */
    private byte[] rsaKey(String key, String role) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("RSA " + role + "不能为空");
        }
        String s = key.replaceAll("-----BEGIN[^-]*-----", "")
                .replaceAll("-----END[^-]*-----", "")
                .replaceAll("\\s+", "");
        try {
            return Codec.base64Decode(s);
        } catch (Exception e) {
            throw new IllegalArgumentException("RSA " + role + "应为 Base64 或 PEM：" + e.getMessage());
        }
    }

    // ===================== 编码 / 摘要 / 转换 =====================

    public String base64Encode(String text) {
        return Codec.base64(Codec.utf8(nullToEmpty(text)));
    }

    public String base64Decode(String text) {
        requireNonEmpty(text);
        try {
            return Codec.utf8(Codec.base64Decode(text.trim()));
        } catch (Exception e) {
            throw new IllegalArgumentException("Base64 解码失败：" + rootMessage(e));
        }
    }

    public String hexEncode(String text) {
        return Codec.hex(Codec.utf8(nullToEmpty(text)));
    }

    public String hexDecode(String text) {
        requireNonEmpty(text);
        try {
            return Codec.utf8(Codec.hexDecode(text.trim()));
        } catch (Exception e) {
            throw new IllegalArgumentException("Hex 解码失败：" + rootMessage(e));
        }
    }

    public String urlEncode(String text) {
        try {
            return URLEncoder.encode(nullToEmpty(text), "UTF-8");
        } catch (Exception e) {
            throw new IllegalStateException("URL 编码失败：" + rootMessage(e), e);
        }
    }

    public String urlDecode(String text) {
        requireNonEmpty(text);
        try {
            return URLDecoder.decode(text.trim(), "UTF-8");
        } catch (Exception e) {
            throw new IllegalArgumentException("URL 解码失败：" + rootMessage(e));
        }
    }

    public String hash(String text, String algo) {
        return digestHex(nullToEmpty(text), algo);
    }

    /** 摘要算法名 → JCE 名。 */
    private String jceName(String algo) {
        String a = algo == null || algo.trim().isEmpty() ? "MD5" : algo.trim().toUpperCase(Locale.ROOT);
        switch (a) {
            case "MD5": return "MD5";
            case "SHA1": return "SHA-1";
            case "SHA256": return "SHA-256";
            case "SHA384": return "SHA-384";
            case "SHA512": return "SHA-512";
            default: throw new IllegalArgumentException("不支持的算法：" + a + "（支持 MD5/SHA1/SHA256/SHA384/SHA512）");
        }
    }

    /** 文本（UTF-8）摘要，返回小写 Hex。 */
    private String digestHex(String text, String algo) {
        // 算法名校验必须在 try 之外：jceName 抛的是 IllegalArgumentException（入参非法），
        // 若被下面的 catch(Exception) 吞掉会改抛 IllegalStateException，调用方无法区分
        // 「算法不支持」和「摘要计算失败」。
        String jce = jceName(algo);
        try {
            MessageDigest md = MessageDigest.getInstance(jce);
            return Codec.hex(md.digest(Codec.utf8(text)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("摘要算法不可用：" + algo, e);
        } catch (Exception e) {
            throw new IllegalStateException("摘要计算失败：" + rootMessage(e), e);
        }
    }

    /** 摘要入参上限（解码后字节数）：Base64 文本约为其 3/4，先按长度粗判，避免先解出巨量内存。 */
    private static final int DIGEST_MAX_BYTES = 20 * 1024 * 1024;

    /**
     * 摘要（MD5/SHA 系列）：文本或二进制文件内容。
     *
     * <p>文件由前端读成 Base64 传上来（{@code contentB64}）——浏览器没有 MD5 的原生实现，
     * 而 {@code crypto.subtle} 既不支持 MD5、又在 http 访问（非安全上下文）下不可用，
     * 所以文件摘要只能落到服务端算。
     *
     * @param text      文本内容（UTF-8），与 contentB64 二选一
     * @param contentB64 文件内容的 Base64（与 text 二选一）
     * @param algos     算法列表（可多个）；为空则用 {@code algo}
     * @param algo      单算法（algos 为空时用）
     * @param upper     Hex 是否大写
     * @return {size: 原始字节数, fromFile: 是否文件, results: {ALGO: {hex, base64}}}
     */
    public Map<String, Object> digest(String text, String contentB64, List<String> algos,
                                      String algo, boolean upper) {
        byte[] raw;
        boolean fromFile;
        if (contentB64 != null && !contentB64.trim().isEmpty()) {
            String b64 = contentB64.replaceAll("\\s", "");
            // 先按长度粗判上限，避免直接解出一个几百 MB 的数组
            if (b64.length() / 4 * 3 > DIGEST_MAX_BYTES) {
                throw new IllegalArgumentException("文件过大：摘要只支持 " + (DIGEST_MAX_BYTES / 1024 / 1024) + " MB 以内");
            }
            try {
                raw = Base64.getDecoder().decode(b64);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("文件内容不是合法 Base64：" + rootMessage(e));
            }
            fromFile = true;
        } else {
            raw = Codec.utf8(nullToEmpty(text));
            fromFile = false;
        }
        List<String> list = (algos == null || algos.isEmpty())
                ? java.util.Collections.singletonList(algo == null ? "MD5" : algo)
                : algos;
        Map<String, Object> results = new LinkedHashMap<>();
        for (String a : list) {
            if (a == null || a.trim().isEmpty()) {
                continue;
            }
            String name = a.trim().toUpperCase(Locale.ROOT);
            try {
                MessageDigest md = MessageDigest.getInstance(jceName(name));
                byte[] d = md.digest(raw);
                String hex = Codec.hex(d);
                Map<String, Object> one = new LinkedHashMap<>();
                one.put("hex", upper ? hex.toUpperCase(Locale.ROOT) : hex);
                one.put("base64", Base64.getEncoder().encodeToString(d));
                results.put(name, one);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("摘要算法不可用：" + name, e);
            }
        }
        if (results.isEmpty()) {
            throw new IllegalArgumentException("请至少指定一种摘要算法");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("size", raw.length);
        out.put("fromFile", fromFile);
        out.put("results", results);
        return out;
    }

    // ===================== HMAC =====================

    /**
     * HMAC 计算（接口签名调试用）。
     *
     * <p>与摘要一样只能在服务端算：浏览器 {@code crypto.subtle} 虽支持 HMAC，
     * 但在 http://<ip> 这类非安全上下文下整个 {@code crypto.subtle} 都不可用，
     * 而工具平台经常就是这么访问的。
     *
     * @param message 待签名内容（UTF-8）
     * @param key     密钥（按 keyEnc 解码）
     * @param algo    MD5/SHA1/SHA256/SHA384/SHA512
     * @param keyEnc  密钥编码：utf8/hex/base64
     * @return {algo, hex, base64}
     */
    public Map<String, Object> hmac(String message, String key, String algo, String keyEnc) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("HMAC 密钥不能为空");
        }
        byte[] k = decodeKey(key, keyEnc, "HMAC");
        String a = algo == null || algo.trim().isEmpty() ? "SHA256" : algo.trim().toUpperCase(Locale.ROOT);
        String jce;
        switch (a) {
            case "MD5": jce = "HmacMD5"; break;
            case "SHA1": jce = "HmacSHA1"; break;
            case "SHA256": jce = "HmacSHA256"; break;
            case "SHA384": jce = "HmacSHA384"; break;
            case "SHA512": jce = "HmacSHA512"; break;
            default:
                throw new IllegalArgumentException("不支持的 HMAC 算法：" + a
                        + "（支持 MD5/SHA1/SHA256/SHA384/SHA512）");
        }
        try {
            Mac mac = Mac.getInstance(jce);
            mac.init(new SecretKeySpec(k, jce));
            byte[] d = mac.doFinal(Codec.utf8(message == null ? "" : message));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("algo", a);
            out.put("hex", Codec.hex(d));
            out.put("base64", Base64.getEncoder().encodeToString(d));
            return out;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("HMAC 算法不可用：" + a, e);
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("HMAC 密钥不合法：" + rootMessage(e));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 计算失败：" + rootMessage(e), e);
        }
    }

    // ===================== 字符集转换 =====================

    /** 可选字符集：名称 → Java 字符集名。 */
    private static final List<String> CHARSET_NAMES = Arrays.asList(
            "UTF-8", "GBK", "GB2312", "Big5", "ISO-8859-1", "UTF-16", "UTF-16LE", "UTF-16BE", "ASCII");

    /**
     * 文本 → 目标字符集的字节（Hex / Base64）。
     *
     * <p>排查中文乱码时最直接的问题就是「这个字在某编码下到底是哪几个字节」，
     * 所以这里把 Unicode 文本按目标字符集编码后给出原始字节。
     *
     * @return {charset, size, hex, base64, lossy, unmappable}
     */
    public Map<String, Object> charsetEncode(String text, String charset) {
        String cs = charset(charset);
        String s = text == null ? "" : text;
        Charset charsetObj = Charset.forName(cs);
        List<String> unmappable = new ArrayList<>();
        byte[] bytes;
        try {
            CharsetEncoder enc = charsetObj.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            ByteBuffer buf = enc.encode(CharBuffer.wrap(s));
            bytes = new byte[buf.remaining()];
            buf.get(bytes);
        } catch (UnmappableCharacterException e) {
            // 逐字符定位无法表示的字符（最多列 10 个），字节仍按 '?' 替换给出，便于对照
            unmappable = unmappableChars(s, charsetObj);
            bytes = s.getBytes(charsetObj);
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("字符集编码失败：" + rootMessage(e));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("charset", cs);
        out.put("size", bytes.length);
        out.put("hex", Codec.hex(bytes));
        out.put("base64", Base64.getEncoder().encodeToString(bytes));
        out.put("lossy", !unmappable.isEmpty());
        out.put("unmappable", unmappable);
        return out;
    }

    /** 找出在目标字符集中无法表示的字符。 */
    private List<String> unmappableChars(String s, Charset charsetObj) {
        List<String> out = new ArrayList<>();
        int limit = Math.min(s.length(), 20000);
        int i = 0;
        while (i < limit && out.size() < 10) {
            int cp = s.codePointAt(i);
            int n = Character.charCount(cp);
            try {
                // 每次新建 encoder：一次失败后 encoder 内部状态不可复用
                CharsetEncoder enc = charsetObj.newEncoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT);
                enc.encode(CharBuffer.wrap(Character.toChars(cp)));
            } catch (CharacterCodingException e) {
                out.add("'" + new String(Character.toChars(cp)) + "' (U+" + String.format("%04X", cp) + ")");
            }
            i += n;
        }
        return out;
    }

    /**
     * 字节（Hex / Base64）→ 文本。
     *
     * @param inEnc 字节的编码形式：hex/base64
     * @return {charset, size, text}
     */
    public Map<String, Object> charsetDecode(String data, String inEnc, String charset) {
        String cs = charset(charset);
        requireNonEmpty(data);
        byte[] bytes;
        String e = inEnc == null ? "hex" : inEnc.trim().toLowerCase(Locale.ROOT);
        try {
            bytes = "base64".equals(e) ? Base64.getDecoder().decode(data.trim()) : Codec.hexDecode(data.trim());
        } catch (Exception ex) {
            throw new IllegalArgumentException("字节按「" + e + "」解析失败：" + rootMessage(ex));
        }
        Charset charsetObj = Charset.forName(cs);
        CharsetDecoder dec = charsetObj.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        String text;
        try {
            text = dec.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException ex) {
            throw new IllegalArgumentException("不是合法的 " + cs + " 字节：" + rootMessage(ex));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("charset", cs);
        out.put("size", bytes.length);
        out.put("text", text);
        return out;
    }

    private String charset(String name) {
        String c = name == null || name.trim().isEmpty() ? "UTF-8" : name.trim();
        // 允许用户填 gb2312 / GBK 这类大小写/别名写法
        for (String supported : CHARSET_NAMES) {
            if (supported.equalsIgnoreCase(c)) {
                return supported;
            }
        }
        try {
            String resolved = Charset.forName(c).name();
            return resolved;
        } catch (Exception e) {
            throw new IllegalArgumentException("不支持的字符集：" + c
                    + "（支持 " + String.join("/", CHARSET_NAMES) + "）");
        }
    }

    /**
     * JSONPath 求值：从 JSON 文本里取值（用于「取响应里的某个值」）。
     *
     * @return {found, value, valueText, type, matches, matchCount}
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> jsonPath(String json, String path) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("JSON 不能为空");
        }
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("JSONPath 不能为空");
        }
        Object doc;
        try {
            doc = JsonUtils.fromJson(json, Object.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON 解析失败：" + rootMessage(e));
        }
        String p = path.trim();
        Map<String, Object> out = new LinkedHashMap<>();
        List<Object> matches = new ArrayList<>();
        boolean found = false;
        Object value = null;
        try {
            // ALWAYS_RETURN_LIST：多值路径（如 $.items[*].id）要能一次拿到全部
            com.jayway.jsonpath.Configuration cfg = com.jayway.jsonpath.Configuration.defaultConfiguration()
                    .addOptions(com.jayway.jsonpath.Option.ALWAYS_RETURN_LIST);
            Object list = com.jayway.jsonpath.JsonPath.using(cfg).parse(doc).read(p);
            if (list instanceof List) {
                matches.addAll((List<Object>) list);
                found = !matches.isEmpty();
                value = found ? matches.get(0) : null;
            } else if (list != null) {
                matches.add(list);
                found = true;
                value = list;
            }
        } catch (com.jayway.jsonpath.PathNotFoundException e) {
            found = false;
        } catch (Exception e) {
            throw new IllegalArgumentException("JSONPath 求值失败：" + rootMessage(e));
        }
        out.put("found", found);
        out.put("value", value);
        out.put("valueText", value == null ? "" : valueText(value));
        out.put("type", value == null ? "" : value.getClass().getSimpleName());
        out.put("matches", matches);
        out.put("matchCount", matches.size());
        return out;
    }

    /** 取值转展示串：标量直转，对象/数组紧凑 JSON，null→""。 */
    private String valueText(Object v) {
        if (v == null) {
            return "";
        }
        if (v instanceof String || v instanceof Number || v instanceof Boolean) {
            return String.valueOf(v);
        }
        try {
            return JsonUtils.toJson(v);
        } catch (Exception e) {
            return String.valueOf(v);
        }
    }

    /** 解析 JWT（不验签）：header / payload 以美化 JSON 返回，signature 原样。 */
    public Map<String, String> jwtDecode(String token) {
        String t = token == null ? "" : token.trim();
        if (t.isEmpty()) {
            throw new IllegalArgumentException("JWT 不能为空");
        }
        String[] parts = t.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("JWT 格式错误：应以 header.payload.signature 结构、用 . 分隔");
        }
        Map<String, String> out = new LinkedHashMap<>();
        out.put("header", prettyJson(b64UrlDecode(parts[0])));
        out.put("payload", prettyJson(b64UrlDecode(parts[1])));
        out.put("signature", parts.length >= 3 ? parts[2] : "(无签名)");
        return out;
    }

    private String b64UrlDecode(String s) {
        String p = s;
        switch (s.length() % 4) {
            case 2: p = s + "=="; break;
            case 3: p = s + "="; break;
            default: break;
        }
        try {
            return Codec.utf8(Base64.getUrlDecoder().decode(p));
        } catch (Exception e) {
            throw new IllegalArgumentException("Base64Url 解码失败：" + rootMessage(e));
        }
    }

    private String prettyJson(String json) {
        try {
            return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(JSON.readTree(json));
        } catch (Exception e) {
            return json;
        }
    }

    // ---------- 时间戳 ----------

    public Map<String, Object> tsNow() {
        return tsMap(Instant.now());
    }

    public Map<String, Object> tsToEpoch(String datetime) {
        return tsMap(parseDateTime(datetime).atZone(ZoneId.systemDefault()).toInstant());
    }

    public Map<String, Object> tsFromEpoch(String value) {
        long v = parseEpoch(value);
        // 不足 13 位（< 1e12）视为秒，否则视为毫秒
        long ms = v < 1_000_000_000_000L ? v * 1000L : v;
        return tsMap(Instant.ofEpochMilli(ms));
    }

    private Map<String, Object> tsMap(Instant inst) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("seconds", inst.getEpochSecond());
        m.put("millis", inst.toEpochMilli());
        m.put("iso", inst.toString());
        m.put("local", LocalDateTime.ofInstant(inst, ZoneId.systemDefault()).withNano(0).toString().replace('T', ' '));
        return m;
    }

    private long parseEpoch(String value) {
        String s = value == null ? "" : value.trim().replaceAll("[^0-9-]", "");
        if (s.isEmpty()) {
            throw new IllegalArgumentException("无法解析为时间戳数字：" + value);
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("无法解析为时间戳数字：" + value);
        }
    }

    private LocalDateTime parseDateTime(String text) {
        String s = text == null ? "" : text.trim();
        if (s.isEmpty()) {
            throw new IllegalArgumentException("日期不能为空");
        }
        Exception last = null;
        try {
            return LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) { last = e; }
        try {
            return LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        } catch (Exception e) { last = e; }
        try {
            return LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) { last = e; }
        try {
            return LocalDate.parse(s).atStartOfDay();
        } catch (Exception e) { last = e; }
        throw new IllegalArgumentException("无法解析日期，支持 yyyy-MM-dd HH:mm:ss / ISO：" + (last == null ? "" : rootMessage(last)));
    }

    // ---------- UUID ----------

    public String uuid(int count) {
        int n = count <= 0 ? 1 : Math.min(count, 100);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(UUID.randomUUID()).append('\n');
        }
        return sb.toString().trim();
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private void requireNonEmpty(String s) {
        if (s == null || s.trim().isEmpty()) {
            throw new IllegalArgumentException("输入不能为空");
        }
    }

    // ===================== 通用编解码 =====================

    private byte[] decodeKey(String key, String enc, String algo) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException(algo + " 密钥不能为空");
        }
        String e = enc == null ? "utf8" : enc.toLowerCase(Locale.ROOT);
        try {
            switch (e) {
                case "hex":
                    return Codec.hexDecode(key);
                case "base64":
                    return Codec.base64Decode(key);
                default:
                    return Codec.utf8(key);
            }
        } catch (Exception ex) {
            throw new IllegalArgumentException("密钥按「" + e + "」解码失败：" + ex.getMessage());
        }
    }

    private byte[] ivBytes(String iv, String enc) {
        if (iv == null || iv.trim().isEmpty()) {
            throw new IllegalArgumentException("CBC 模式需要 IV");
        }
        return decodeKey(iv, enc, "IV");
    }

    private String encode(byte[] data, String enc) {
        return "hex".equalsIgnoreCase(enc) ? Codec.hex(data) : Codec.base64(data);
    }

    private byte[] decode(String data, String enc) {
        String s = data == null ? "" : data.trim();
        if (s.isEmpty()) {
            throw new IllegalArgumentException("密文/签名不能为空");
        }
        try {
            return "hex".equalsIgnoreCase(enc) ? Codec.hexDecode(s) : Codec.base64Decode(s);
        } catch (Exception e) {
            throw new IllegalArgumentException("按「" + enc + "」解码失败：" + e.getMessage());
        }
    }

    private String rootMessage(Throwable e) {
        Throwable c = e;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        String msg = c.getMessage();
        return msg == null ? c.toString() : msg;
    }
}
