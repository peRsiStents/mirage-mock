package com.miragemock.admin.service;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miragemock.dsl.crypto.ClassicCrypto;
import com.miragemock.dsl.crypto.Codec;
import com.miragemock.dsl.crypto.SmCrypto;
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
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
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
        String a = algo == null ? "MD5" : algo.toUpperCase(Locale.ROOT);
        String jce;
        switch (a) {
            case "MD5": jce = "MD5"; break;
            case "SHA1": jce = "SHA-1"; break;
            case "SHA256": jce = "SHA-256"; break;
            case "SHA512": jce = "SHA-512"; break;
            default: throw new IllegalArgumentException("不支持的算法：" + a + "（支持 MD5/SHA1/SHA256/SHA512）");
        }
        try {
            MessageDigest md = MessageDigest.getInstance(jce);
            return Codec.hex(md.digest(Codec.utf8(nullToEmpty(text))));
        } catch (Exception e) {
            throw new IllegalStateException("摘要计算失败：" + rootMessage(e), e);
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
