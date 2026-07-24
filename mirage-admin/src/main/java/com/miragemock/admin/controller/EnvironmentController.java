package com.miragemock.admin.controller;

import com.miragemock.admin.service.EnvironmentService;
import com.miragemock.common.api.Result;
import com.miragemock.common.entity.TestEnvironment;
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

/** 测试环境管理 */
@RestController
@RequestMapping("/api/v1")
public class EnvironmentController {

    private final EnvironmentService service;

    @Autowired
    public EnvironmentController(EnvironmentService service) {
        this.service = service;
    }

    @GetMapping("/projects/{pid}/environments")
    public Result<List<TestEnvironment>> list(@PathVariable Long pid) {
        return Result.ok(service.list(pid));
    }

    @PostMapping("/projects/{pid}/environments")
    public Result<TestEnvironment> create(@PathVariable Long pid, @RequestBody TestEnvironment e) {
        e.setProjectId(pid);
        return Result.ok(service.create(e));
    }

    @PutMapping("/environments/{id}")
    public Result<TestEnvironment> update(@PathVariable Long id, @RequestBody TestEnvironment e) {
        return Result.ok(service.update(id, e));
    }

    @DeleteMapping("/environments/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }
}
