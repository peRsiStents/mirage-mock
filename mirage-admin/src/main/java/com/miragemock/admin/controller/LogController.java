package com.miragemock.admin.controller;

import com.miragemock.admin.service.LogService;
import com.miragemock.common.api.PageResult;
import com.miragemock.common.api.Result;
import com.miragemock.common.entity.MockRequestLog;
import com.miragemock.common.entity.MockRule;
import com.miragemock.common.entity.TestCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{pid}/logs")
public class LogController {

    private final LogService logService;

    @Autowired
    public LogController(LogService logService) {
        this.logService = logService;
    }

    @GetMapping
    public Result<PageResult<MockRequestLog>> query(
            @PathVariable Long pid,
            @RequestParam(required = false) Long interfaceId,
            @RequestParam(required = false) Integer matched,
            @RequestParam(required = false) String protocol,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long from,
            @RequestParam(required = false) Long to,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return Result.ok(logService.query(pid, interfaceId, matched, protocol, keyword, from, to, page, size));
    }

    /** 单条日志完整原文（详情懒加载）。 */
    @GetMapping("/{logId}")
    public Result<MockRequestLog> get(@PathVariable Long pid, @PathVariable Long logId) {
        return Result.ok(logService.get(logId));
    }

    /** 将一条请求日志解析为「未保存」的测试用例草稿（前端打开编辑、复核后再保存）。 */
    @PostMapping("/{logId}/testcase")
    public Result<TestCase> toTestCase(@PathVariable Long pid, @PathVariable Long logId) {
        return Result.ok(logService.buildTestCaseDraft(pid, logId));
    }

    /** 将一条请求日志解析为「未保存」的 Mock 规则草稿（响应模板启发式 DSL 化，复核后保存）。 */
    @PostMapping("/{logId}/rule")
    public Result<MockRule> toRuleDraft(@PathVariable Long pid, @PathVariable Long logId) {
        return Result.ok(logService.buildRuleDraft(pid, logId));
    }
}
