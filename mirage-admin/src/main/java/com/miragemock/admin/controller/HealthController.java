package com.miragemock.admin.controller;

import com.miragemock.common.api.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查（liveness）：供负载均衡 / 监控 / CI 探活，无需登录。
 * 仅返回进程存活信号与基本运行信息，不暴露敏感配置。
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", "UP");
        m.put("uptimeMs", ManagementFactory.getRuntimeMXBean().getUptime());
        m.put("time", System.currentTimeMillis());
        m.put("java", System.getProperty("java.version", "unknown"));
        m.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        return Result.ok(m);
    }
}
