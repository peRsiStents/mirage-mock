package com.miragemock.http.proxy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 录制响应匹配键：method|path|sortedQuery|bodyHash 的 MD5。
 * 同一请求（含 query 参数与 body）复用同一份录制快照。
 */
public final class RecordKey {

    private RecordKey() {
    }

    /**
     * @param query 原始 query 字符串（可含 URL 编码）；null 视为空
     */
    public static String of(String method, String path, String query, String bodyRaw) {
        String m = method == null ? "" : method.trim().toUpperCase();
        String p = path == null ? "" : path;
        String q = normalizeQuery(query);
        String bodyHash = md5(bodyRaw == null ? "" : bodyRaw);
        return md5(m + "|" + p + "|" + q + "|" + bodyHash);
    }

    /** query 参数排序后重组，保证参数顺序变化不影响匹配 */
    static String normalizeQuery(String query) {
        if (query == null || query.isEmpty()) {
            return "";
        }
        List<String> pairs = new ArrayList<>();
        for (String pair : query.split("&")) {
            if (!pair.isEmpty()) {
                pairs.add(pair);
            }
        }
        Collections.sort(pairs);
        return String.join("&", pairs);
    }

    private static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("MD5 不可用", e);
        }
    }
}
