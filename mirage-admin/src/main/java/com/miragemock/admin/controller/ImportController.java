package com.miragemock.admin.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.miragemock.admin.service.OpenApiImportService;
import com.miragemock.common.api.Result;
import com.miragemock.common.api.ResultCode;
import com.miragemock.common.exception.BizException;
import com.miragemock.common.util.JsonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 导入：OpenAPI/Swagger 文档 → 批量生成接口与兜底规则。
 */
@RestController
@RequestMapping("/api/v1/import")
public class ImportController {

    private final OpenApiImportService importService;

    @Autowired
    public ImportController(OpenApiImportService importService) {
        this.importService = importService;
    }

    /**
     * 导入 OpenAPI 3 文档。
     * body: {"projectId": 1, "spec": <OpenAPI JSON 对象或 JSON 字符串>}
     */
    @PostMapping("/openapi")
    public Result<OpenApiImportService.ImportResult> importOpenApi(@RequestBody JsonNode body) {
        JsonNode pidNode = body == null ? null : body.get("projectId");
        if (pidNode == null || !pidNode.isNumber()) {
            throw new BizException(ResultCode.BAD_REQUEST, "缺少 projectId");
        }
        JsonNode spec = body.get("spec");
        if (spec == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "缺少 spec");
        }
        if (spec.isTextual()) {
            try {
                spec = JsonUtils.mapper().readTree(spec.asText());
            } catch (Exception e) {
                throw new BizException(ResultCode.BAD_REQUEST, "spec 不是合法 JSON: " + e.getMessage());
            }
        }
        return Result.ok(importService.importOpenApi(pidNode.asLong(), spec));
    }
}
