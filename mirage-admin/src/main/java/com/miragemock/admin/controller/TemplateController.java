package com.miragemock.admin.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.miragemock.admin.dto.EvaluateRequest;
import com.miragemock.common.api.Result;
import com.miragemock.common.util.JsonUtils;
import com.miragemock.core.engine.MockEngine;
import com.miragemock.core.render.RenderedResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 模板试算：即时渲染预览。
 */
@RestController
@RequestMapping("/api/v1/template")
public class TemplateController {

    private final MockEngine engine;

    @Autowired
    public TemplateController(MockEngine engine) {
        this.engine = engine;
    }

    @PostMapping("/evaluate")
    public Result<RenderedResponse> evaluate(@RequestBody EvaluateRequest request) {
        JsonNode node = request.getTemplate() == null
                ? null
                : JsonUtils.mapper().valueToTree(request.getTemplate());
        Map<String, Object> baseVars = new HashMap<>();
        if (request.getContext() != null) {
            flatten("", request.getContext(), baseVars);
        }
        return Result.ok(engine.renderForEval(node, baseVars, request.getProjectId()));
    }

    @SuppressWarnings("unchecked")
    private void flatten(String prefix, Map<String, Object> src, Map<String, Object> dst) {
        for (Map.Entry<String, Object> e : src.entrySet()) {
            String key = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            if (e.getValue() instanceof Map) {
                flatten(key, (Map<String, Object>) e.getValue(), dst);
            } else {
                dst.put(key, e.getValue());
            }
        }
    }
}
