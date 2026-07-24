package com.miragemock.admin.controller;

import com.miragemock.admin.dto.RunResult;
import com.miragemock.admin.service.TestCaseService;
import com.miragemock.common.api.Result;
import com.miragemock.common.entity.TestCase;
import com.miragemock.common.entity.TestRunLog;
import com.miragemock.common.entity.TestVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 测试案例管理 REST API：CRUD + 运行(proxy) + 运行历史。
 */
@RestController
@RequestMapping("/api/v1")
public class TestCaseController {

    private final TestCaseService service;

    @Autowired
    public TestCaseController(TestCaseService service) {
        this.service = service;
    }

    @GetMapping("/projects/{pid}/testcases")
    public Result<List<TestCase>> list(@PathVariable Long pid) {
        return Result.ok(service.list(pid));
    }

    @PostMapping("/projects/{pid}/testcases")
    public Result<TestCase> create(@PathVariable Long pid, @RequestBody TestCase t) {
        t.setProjectId(pid);
        return Result.ok(service.create(t));
    }

    @PutMapping("/testcases/{id}")
    public Result<TestCase> update(@PathVariable Long id, @RequestBody TestCase t) {
        return Result.ok(service.update(id, t));
    }

    @DeleteMapping("/testcases/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }

    /** 运行（proxy：后端转发 + 断言求值 + 记录历史） */
    @PostMapping("/testcases/{id}/run")
    public Result<RunResult> run(@PathVariable Long id) {
        return Result.ok(service.run(id));
    }

    /** 运行历史（最近 100 条） */
    @GetMapping("/testcases/{id}/runs")
    public Result<List<TestRunLog>> runs(@PathVariable Long id) {
        return Result.ok(service.runs(id));
    }

    // ============ 测试变量/常量（项目级） ============

    @GetMapping("/projects/{pid}/test-variables")
    public Result<List<TestVariable>> listVariables(@PathVariable Long pid) {
        return Result.ok(service.listVariables(pid));
    }

    @PostMapping("/projects/{pid}/test-variables")
    public Result<TestVariable> createVariable(@PathVariable Long pid, @RequestBody TestVariable v) {
        v.setProjectId(pid);
        return Result.ok(service.createVariable(v));
    }

    @PutMapping("/test-variables/{id}")
    public Result<TestVariable> updateVariable(@PathVariable Long id, @RequestBody TestVariable v) {
        return Result.ok(service.updateVariable(id, v));
    }

    @DeleteMapping("/test-variables/{id}")
    public Result<Void> deleteVariable(@PathVariable Long id) {
        service.deleteVariable(id);
        return Result.ok();
    }
}
