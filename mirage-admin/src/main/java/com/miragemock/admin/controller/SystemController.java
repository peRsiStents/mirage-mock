package com.miragemock.admin.controller;

import com.miragemock.common.api.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统信息：向前端暴露 Mock 访问端口，用于在界面拼装可复制的 Mock 访问链接。
 *
 * <p>用 {@code @Value} 直接读取 {@code mirage.http.*}，避免管理端模块反向依赖 mirage-http。
 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    @Value("${mirage.http.enabled:true}")
    private boolean httpEnabled;

    @Value("${mirage.http.port:19080}")
    private int httpPort;

    @GetMapping("/info")
    public Result<Map<String, Object>> info() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("httpEnabled", httpEnabled);
        data.put("httpPort", httpPort);
        return Result.ok(data);
    }
}
