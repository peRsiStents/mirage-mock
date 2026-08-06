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
import org.springframework.web.bind.annotation.RequestParam;
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

    @DeleteMapping("/rules/batch")
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        ruleService.deleteBatch(ids);
        return Result.ok();
    }

    @PostMapping("/rules/{id}/toggle")
    public Result<MockRule> toggle(@PathVariable Long id) {
        return Result.ok(ruleService.toggle(id));
    }

    @PutMapping("/rules/{id}/priority")
    public Result<Void> setPriority(@PathVariable Long id, @RequestParam Integer priority) {
        ruleService.setPriority(id, priority);
        return Result.ok();
    }

    @PutMapping("/rules/batch/status")
    public Result<Void> setBatchStatus(@RequestBody java.util.Map<String, Object> body) {
        Object idsObj = body.get("ids");
        Object statusObj = body.get("status");
        if (!(idsObj instanceof java.util.List) || statusObj == null) {
            return Result.ok();
        }
        java.util.List<Long> ids = new java.util.ArrayList<>();
        for (Object o : (java.util.List<?>) idsObj) {
            ids.add(Long.valueOf(String.valueOf(o)));
        }
        ruleService.setBatchStatus(ids, Integer.valueOf(String.valueOf(statusObj)));
        return Result.ok();
    }
}
