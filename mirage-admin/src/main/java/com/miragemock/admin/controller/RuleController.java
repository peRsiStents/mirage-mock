package com.miragemock.admin.controller;

import com.miragemock.admin.dto.RuleRequest;
import com.miragemock.admin.service.RuleService;
import com.miragemock.common.api.Result;
import com.miragemock.common.entity.MockRule;
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

@RestController
@RequestMapping("/api/v1")
public class RuleController {

    private final RuleService ruleService;

    @Autowired
    public RuleController(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    @GetMapping("/interfaces/{iid}/rules")
    public Result<List<MockRule>> list(@PathVariable Long iid) {
        return Result.ok(ruleService.list(iid));
    }

    @PostMapping("/interfaces/{iid}/rules")
    public Result<MockRule> create(@PathVariable Long iid, @RequestBody RuleRequest request) {
        return Result.ok(ruleService.create(iid, request));
    }

    @GetMapping("/rules/{id}")
    public Result<MockRule> get(@PathVariable Long id) {
        return Result.ok(ruleService.get(id));
    }

    @PutMapping("/rules/{id}")
    public Result<MockRule> update(@PathVariable Long id, @RequestBody RuleRequest request) {
        return Result.ok(ruleService.update(id, request));
    }

    @DeleteMapping("/rules/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        ruleService.delete(id);
        return Result.ok();
    }

    @PostMapping("/rules/{id}/toggle")
    public Result<MockRule> toggle(@PathVariable Long id) {
        return Result.ok(ruleService.toggle(id));
    }
}
