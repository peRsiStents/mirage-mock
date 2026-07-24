package com.miragemock.admin.controller;

import com.miragemock.admin.service.ScenarioService;
import com.miragemock.common.api.PageResult;
import com.miragemock.common.api.Result;
import com.miragemock.common.entity.TestRunRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 测试报告：运行记录列表 + 详情 */
@RestController
@RequestMapping("/api/v1")
public class TestReportController {

    private final ScenarioService service;

    @Autowired
    public TestReportController(ScenarioService service) {
        this.service = service;
    }

    @GetMapping("/projects/{pid}/records")
    public Result<PageResult<TestRunRecord>> records(@PathVariable Long pid,
                                                     @RequestParam(required = false) String type,
                                                     @RequestParam(required = false) Long targetId,
                                                     @RequestParam(defaultValue = "1") long page,
                                                     @RequestParam(defaultValue = "20") long size) {
        return Result.ok(service.records(pid, type, targetId, page, size));
    }

    @GetMapping("/records/{id}")
    public Result<TestRunRecord> record(@PathVariable Long id) {
        return Result.ok(service.record(id));
    }
}
