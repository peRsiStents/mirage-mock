package com.miragemock.admin.controller;

import com.miragemock.admin.dto.BatchCaseRequest;
import com.miragemock.admin.dto.BatchRunResult;
import com.miragemock.admin.dto.CaseRunResult;
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
import org.springframework.web.bind.annotation.RequestParam;
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

    /** 用例详情：列表已瘦身，编辑/运行/导出按需懒加载全量大字段。 */
    @GetMapping("/testcases/{id}")
    public Result<TestCase> get(@PathVariable Long id) {
        return Result.ok(service.get(id));
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

    @DeleteMapping("/testcases/batch")
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        service.deleteBatch(ids);
        return Result.ok();
    }

    /** 运行（proxy：后端转发 + 断言求值 + 记录历史；可选 envId 指定环境） */
    @PostMapping("/testcases/{id}/run")
    public Result<RunResult> run(@PathVariable Long id, @RequestParam(required = false) Long envId) {
        return Result.ok(service.run(id, envId));
    }

    /** 数据驱动运行（按 dataSet 行逐行跑，汇总结果） */
    @PostMapping("/testcases/{id}/run-data")
    public Result<CaseRunResult> runData(@PathVariable Long id, @RequestParam(required = false) Long envId) {
        return Result.ok(service.runData(id, envId));
    }

    /** 批量运行（选中多条用例一起跑，返回每条摘要 + 汇总） */
    @PostMapping("/projects/{pid}/testcases/run")
    public Result<BatchRunResult> batchRun(@PathVariable Long pid, @RequestBody BatchCaseRequest req) {
        return Result.ok(service.batchRun(req.getIds(), pid, req.getEnvId()));
    }

    /** 批量启用/停用（status: 1=启用 0=停用） */
    @PutMapping("/projects/{pid}/testcases/batch-status")
    public Result<Void> batchStatus(@PathVariable Long pid, @RequestBody BatchCaseRequest req) {
        service.setBatchStatus(req.getIds(), req.getStatus());
        return Result.ok();
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
