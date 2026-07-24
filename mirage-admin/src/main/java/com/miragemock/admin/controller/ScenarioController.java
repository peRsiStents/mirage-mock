package com.miragemock.admin.controller;

import com.miragemock.admin.dto.ScenarioRunResult;
import com.miragemock.admin.service.ScenarioService;
import com.miragemock.common.api.Result;
import com.miragemock.common.entity.TestScenario;
import com.miragemock.common.entity.TestScenarioStep;
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

/** 测试场景：CRUD + 步骤 + 运行 */
@RestController
@RequestMapping("/api/v1")
public class ScenarioController {

    private final ScenarioService service;

    @Autowired
    public ScenarioController(ScenarioService service) {
        this.service = service;
    }

    @GetMapping("/projects/{pid}/scenarios")
    public Result<List<TestScenario>> list(@PathVariable Long pid) {
        return Result.ok(service.list(pid));
    }

    @PostMapping("/projects/{pid}/scenarios")
    public Result<TestScenario> create(@PathVariable Long pid, @RequestBody TestScenario s) {
        s.setProjectId(pid);
        return Result.ok(service.create(s));
    }

    @PutMapping("/scenarios/{id}")
    public Result<TestScenario> update(@PathVariable Long id, @RequestBody TestScenario s) {
        return Result.ok(service.update(id, s));
    }

    @DeleteMapping("/scenarios/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }

    @GetMapping("/scenarios/{id}/steps")
    public Result<List<TestScenarioStep>> steps(@PathVariable Long id) {
        return Result.ok(service.steps(id));
    }

    /** 整体替换步骤（按数组顺序为 seq） */
    @PutMapping("/scenarios/{id}/steps")
    public Result<List<TestScenarioStep>> saveSteps(@PathVariable Long id, @RequestBody List<TestScenarioStep> steps) {
        return Result.ok(service.saveSteps(id, steps));
    }

    /** 运行场景（可选 envId 覆盖默认环境） */
    @PostMapping("/scenarios/{id}/run")
    public Result<ScenarioRunResult> run(@PathVariable Long id, @RequestParam(required = false) Long envId) {
        return Result.ok(service.runScenario(id, envId));
    }
}
