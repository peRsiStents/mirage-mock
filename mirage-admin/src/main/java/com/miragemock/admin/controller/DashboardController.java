package com.miragemock.admin.controller;

import com.miragemock.admin.service.DashboardService;
import com.miragemock.common.api.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 工作台首页：按项目聚合资源数量、Mock 流量、测试通过率与最近活动。
 */
@RestController
@RequestMapping("/api/v1")
public class DashboardController {

    private final DashboardService service;

    @Autowired
    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/projects/{pid}/dashboard")
    public Result<Map<String, Object>> overview(@PathVariable Long pid) {
        return Result.ok(service.overview(pid));
    }
}
