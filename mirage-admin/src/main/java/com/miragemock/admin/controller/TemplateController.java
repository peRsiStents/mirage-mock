package com.miragemock.admin.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.miragemock.admin.dto.EvaluateRequest;
import com.miragemock.admin.security.ProjectAuthz;
import com.miragemock.common.api.Result;
import com.miragemock.common.api.ResultCode;
import com.miragemock.common.exception.BizException;
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
    private final ProjectAuthz authz;

    @Autowired
    public TemplateController(MockEngine engine, ProjectAuthz authz) {
        this.engine = engine;
        this.authz = authz;
    }

    @PostMapping("/evaluate")
    public Result<RenderedResponse> evaluate(@RequestBody EvaluateRequest request) {
        if (request.getProjectId() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "projectId 不能为空");
        }
        // 模板可调用 ${sm*_encrypt(..,'别名')} / ${seq(..)} 等带项目上下文函数，须校验项目成员
        authz.requireMember(request.getProjectId());
        JsonNode node = request.getTemplate() == null
                ? null
                : JsonUtils.mapper().valueToTree(request.getTemplate());
        Map<String, Object> baseVars = new HashMap<>();
        if (request.getContext() != null) {
            flatten("", request.getContext(), baseVars);
        }
        // 严格模式：表达式主体位置的未知变量（拼写错误/上下文缺失）直接报错，而非静默降级为字面量
        return Result.ok(engine.renderForEval(node, baseVars, request.getProjectId(), true));
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
