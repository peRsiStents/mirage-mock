package com.miragemock.core.render;

import com.fasterxml.jackson.databind.JsonNode;
import com.miragemock.common.api.ResultCode;
import com.miragemock.common.exception.BizException;
import com.miragemock.common.util.JsonUtils;
import com.miragemock.dsl.eval.EvalContext;
import com.miragemock.dsl.eval.ExprException;
import com.miragemock.dsl.eval.ExpressionEvaluator;
import com.miragemock.dsl.eval.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 响应模板渲染器：
 * <ol>
 *   <li>解析模板 JSON（HTTP: {status,headers,body}；否则整体作为 body）</li>
 *   <li>收集叶子，按字段依赖拓扑排序求值（支持同级/上级字段引用），循环依赖报错</li>
 *   <li>支持 ${repeat(n, {子模板})} 生成数组；子模板内同样支持表达式</li>
 * </ol>
 */
@Component
public class TemplateRenderer {

    private static final Logger log = LoggerFactory.getLogger(TemplateRenderer.class);
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*");

    private final ExpressionEvaluator evaluator;

    @Autowired
    public TemplateRenderer(ExpressionEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    /**
     * 渲染模板 JSON 节点。
     *
     * @param templateNode 响应模板（已解析的 JSON 节点）
     * @param baseVars     基础变量（path.* 等）
     * @param baseCtx      提供密钥解析器 / 序列提供者 / projectId
     */
    public RenderedResponse render(JsonNode templateNode, Map<String, Object> baseVars, EvalContext baseCtx) {
        if (templateNode == null || templateNode.isNull()) {
            return RenderedResponse.of(200, null, null);
        }
        Map<String, Object> vars = baseVars == null ? new HashMap<>() : new HashMap<>(baseVars);
        EvalContext workCtx = new EvalContext(vars, baseCtx.getSecretResolver(), baseCtx.getSeqProvider(), baseCtx.getProjectId());

        int status = 200;
        Map<String, String> headers = null;
        Object bodyTree;

        if (templateNode.isObject() && templateNode.has("body")) {
            JsonNode statusNode = templateNode.get("status");
            if (statusNode != null && statusNode.isInt()) {
                status = statusNode.asInt();
            }
            JsonNode headersNode = templateNode.get("headers");
            if (headersNode != null && headersNode.isObject()) {
                headers = renderHeaders(headersNode, workCtx);
            }
            bodyTree = toJsonTree(templateNode.get("body"));
        } else {
            bodyTree = toJsonTree(templateNode);
        }

        Object rendered = renderTree(bodyTree, vars, workCtx);
        return RenderedResponse.of(status, headers, rendered);
    }

    private Map<String, String> renderHeaders(JsonNode headersNode, EvalContext ctx) {
        Map<String, String> result = new LinkedHashMap<>();
        headersNode.fields().forEachRemaining(e -> {
            String raw = e.getValue().isTextual() ? e.getValue().asText() : JsonUtils.toJson(toJsonTree(e.getValue()));
            result.put(e.getKey(), String.valueOf(evaluator.evalTemplate(raw, ctx)));
        });
        return result;
    }

    /**
     * 渲染一棵对象树（body 或 repeat 子模板）。
     */
    public Object renderTree(Object bodyTree, Map<String, Object> seedVars, EvalContext baseCtx) {
        if (bodyTree == null) {
            return null;
        }
        Map<String, Object> vars = new HashMap<>(seedVars);
        EvalContext workCtx = new EvalContext(vars, baseCtx.getSecretResolver(), baseCtx.getSeqProvider(), baseCtx.getProjectId());

        List<Leaf> leaves = new ArrayList<>();
        collectLeaves(bodyTree, "", leaves);

        List<Leaf> normal = new ArrayList<>();
        List<Leaf> repeats = new ArrayList<>();
        for (Leaf l : leaves) {
            if (l.repeat) {
                repeats.add(l);
            } else {
                normal.add(l);
            }
        }
        Set<String> allPaths = new HashSet<>();
        for (Leaf l : leaves) {
            allPaths.add(l.path);
        }

        List<Leaf> ordered = topoSort(normal, allPaths);
        for (Leaf l : ordered) {
            if (!l.raw.contains("${")) {
                vars.put(l.path, l.raw);
                continue;
            }
            try {
                vars.put(l.path, evaluator.evalTemplate(l.raw, workCtx));
            } catch (ExprException e) {
                throw new BizException(ResultCode.TEMPLATE_RENDER_ERROR, "字段 " + l.path + " 渲染失败: " + e.getMessage(), e);
            }
        }
        for (Leaf l : repeats) {
            vars.put(l.path, renderRepeat(l.raw, vars, baseCtx));
        }
        return rebuild(bodyTree, "", vars);
    }

    private Object renderRepeat(String raw, Map<String, Object> outerVars, EvalContext baseCtx) {
        String trimmed = raw.trim();
        // ${repeat(N,{...})}
        String inner = trimmed.substring(2, trimmed.length() - 1); // repeat(N,{...})
        String argsText = inner.substring("repeat(".length());
        argsText = argsText.substring(0, argsText.length() - 1); // N,{...}
        List<String> parts = Parser.splitTopLevelComma(argsText);
        if (parts.size() < 2) {
            throw new BizException(ResultCode.TEMPLATE_RENDER_ERROR, "repeat 参数不足: " + raw);
        }
        int n;
        try {
            n = Integer.parseInt(parts.get(0).trim());
        } catch (NumberFormatException e) {
            throw new BizException(ResultCode.TEMPLATE_RENDER_ERROR, "repeat 次数非法: " + parts.get(0));
        }
        Object subTree = toJsonTree(JsonUtils.readTree(parts.get(1)));
        List<Object> arr = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            arr.add(renderTree(subTree, outerVars, baseCtx));
        }
        return arr;
    }

    // ============ 叶子收集与重建 ============

    private static final class Leaf {
        final String path;
        final String raw;
        final boolean repeat;

        Leaf(String path, String raw, boolean repeat) {
            this.path = path;
            this.raw = raw;
            this.repeat = repeat;
        }
    }

    @SuppressWarnings("unchecked")
    private void collectLeaves(Object node, String path, List<Leaf> out) {
        if (node instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) node;
            for (Map.Entry<String, Object> e : map.entrySet()) {
                collectLeaves(e.getValue(), childPath(path, e.getKey()), out);
            }
        } else if (node instanceof List) {
            List<Object> list = (List<Object>) node;
            for (int i = 0; i < list.size(); i++) {
                collectLeaves(list.get(i), path + "[" + i + "]", out);
            }
        } else if (node instanceof String) {
            String raw = (String) node;
            String t = raw.trim();
            boolean rep = t.startsWith("${repeat") && t.endsWith("}");
            out.add(new Leaf(path, raw, rep));
        }
    }

    @SuppressWarnings("unchecked")
    private Object rebuild(Object node, String path, Map<String, Object> vars) {
        if (node instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) node;
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : map.entrySet()) {
                out.put(e.getKey(), rebuild(e.getValue(), childPath(path, e.getKey()), vars));
            }
            return out;
        } else if (node instanceof List) {
            List<Object> list = (List<Object>) node;
            List<Object> out = new ArrayList<>(list.size());
            for (int i = 0; i < list.size(); i++) {
                out.add(rebuild(list.get(i), path + "[" + i + "]", vars));
            }
            return out;
        } else if (node instanceof String) {
            return vars.get(path);
        }
        return node;
    }

    private String childPath(String prefix, String key) {
        return prefix.isEmpty() ? key : prefix + "." + key;
    }

    // ============ 拓扑排序 ============

    private List<Leaf> topoSort(List<Leaf> leaves, Set<String> allPaths) {
        Map<String, Leaf> byPath = new HashMap<>();
        for (Leaf l : leaves) {
            byPath.put(l.path, l);
        }
        Map<String, Set<String>> deps = new HashMap<>();
        Map<String, Integer> indeg = new HashMap<>();
        for (Leaf l : leaves) {
            indeg.put(l.path, 0);
            deps.put(l.path, new HashSet<>());
        }
        for (Leaf l : leaves) {
            Matcher m = TOKEN.matcher(l.raw);
            while (m.find()) {
                String token = m.group();
                if (!token.equals(l.path) && allPaths.contains(token) && byPath.containsKey(token)) {
                    if (deps.get(l.path).add(token)) {
                        indeg.merge(l.path, 1, Integer::sum);
                    }
                }
            }
        }
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> e : indeg.entrySet()) {
            if (e.getValue() == 0) {
                queue.add(e.getKey());
            }
        }
        List<Leaf> ordered = new ArrayList<>();
        int processed = 0;
        while (!queue.isEmpty()) {
            String p = queue.poll();
            ordered.add(byPath.get(p));
            processed++;
            for (Leaf l : leaves) {
                if (deps.get(l.path).contains(p)) {
                    int d = indeg.merge(l.path, -1, Integer::sum);
                    if (d == 0) {
                        queue.add(l.path);
                    }
                }
            }
        }
        if (processed != leaves.size()) {
            throw new BizException(ResultCode.TEMPLATE_CYCLE, "模板字段存在循环依赖");
        }
        return ordered;
    }

    // ============ 工具 ============

    private Object toJsonTree(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return JsonUtils.mapper().treeToValue(node, Object.class);
        } catch (Exception e) {
            throw new BizException(ResultCode.TEMPLATE_RENDER_ERROR, "模板结构解析失败: " + e.getMessage(), e);
        }
    }
}
