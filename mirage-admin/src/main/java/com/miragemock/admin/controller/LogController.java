package com.miragemock.admin.controller;

import com.miragemock.admin.service.LogService;
import com.miragemock.common.api.PageResult;
import com.miragemock.common.api.Result;
import com.miragemock.common.entity.MockRequestLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{pid}/logs")
public class LogController {

    private final LogService logService;

    @Autowired
    public LogController(LogService logService) {
        this.logService = logService;
    }

    @GetMapping
    public Result<PageResult<MockRequestLog>> query(
            @PathVariable Long pid,
            @RequestParam(required = false) Long interfaceId,
            @RequestParam(required = false) Integer matched,
            @RequestParam(required = false) Long from,
            @RequestParam(required = false) Long to,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return Result.ok(logService.query(pid, interfaceId, matched, from, to, page, size));
    }
}
