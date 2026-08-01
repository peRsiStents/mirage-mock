package com.miragemock.admin.controller;

import com.miragemock.admin.dto.ScenarioRunResult;
import com.miragemock.admin.service.ScheduleService;
import com.miragemock.common.api.Result;
import com.miragemock.common.entity.TestSchedule;
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

/** 定时任务管理 REST API */
@RestController
@RequestMapping("/api/v1")
public class ScheduleController {

    private final ScheduleService service;

    @Autowired
    public ScheduleController(ScheduleService service) {
        this.service = service;
    }

    @GetMapping("/projects/{pid}/schedules")
    public Result<List<TestSchedule>> list(@PathVariable Long pid) {
        return Result.ok(service.list(pid));
    }

    @PostMapping("/projects/{pid}/schedules")
    public Result<TestSchedule> create(@PathVariable Long pid, @RequestBody TestSchedule s) {
        s.setProjectId(pid);
        return Result.ok(service.create(s));
    }

    @PutMapping("/schedules/{id}")
    public Result<TestSchedule> update(@PathVariable Long id, @RequestBody TestSchedule s) {
        return Result.ok(service.update(id, s));
    }

    @DeleteMapping("/schedules/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }

    @PostMapping("/schedules/{id}/toggle")
    public Result<TestSchedule> toggle(@PathVariable Long id) {
        return Result.ok(service.toggle(id));
    }

    @PostMapping("/schedules/{id}/run")
    public Result<ScenarioRunResult> run(@PathVariable Long id) {
        return Result.ok(service.runNow(id));
    }

    /** 校验 cron 并预览接下来几次触发时间（与调度器同源）。非法返回 400 + 错误信息。 */
    @GetMapping("/schedules/cron-preview")
    public Result<java.util.List<String>> cronPreview(@RequestParam String cron,
                                                      @RequestParam(defaultValue = "3") int count) {
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        java.util.List<String> times = new java.util.ArrayList<>();
        for (java.time.LocalDateTime t : service.cronPreview(cron, count)) {
            times.add(t.format(fmt));
        }
        return Result.ok(times);
    }
}
