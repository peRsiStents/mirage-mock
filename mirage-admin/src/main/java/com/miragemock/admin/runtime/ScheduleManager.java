package com.miragemock.admin.runtime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.miragemock.admin.dto.ScenarioRunResult;
import com.miragemock.admin.mapper.TestScheduleMapper;
import com.miragemock.admin.service.ScenarioService;
import com.miragemock.common.entity.TestSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 定时任务调度管理器：启动后从库加载 enabled 调度并注册；建/改/删时实时注册/取消。
 */
@Component
public class ScheduleManager {

    private static final Logger log = LoggerFactory.getLogger(ScheduleManager.class);

    private final TestScheduleMapper scheduleMapper;
    private final ScenarioService scenarioService;
    private final ThreadPoolTaskScheduler taskScheduler;
    private final Map<Long, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();

    @Autowired
    public ScheduleManager(TestScheduleMapper scheduleMapper, ScenarioService scenarioService,
                           ThreadPoolTaskScheduler taskScheduler) {
        this.scheduleMapper = scheduleMapper;
        this.scenarioService = scenarioService;
        this.taskScheduler = taskScheduler;
    }

    /** 应用就绪后加载所有 enabled 调度 */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        List<TestSchedule> enabled = scheduleMapper.selectList(
                new LambdaQueryWrapper<TestSchedule>().eq(TestSchedule::getEnabled, 1));
        for (TestSchedule s : enabled) {
            register(s);
        }
        log.info("定时任务已加载 {} 个", enabled.size());
    }

    /** 注册（取消旧的后用新 cron 调度） */
    public void register(TestSchedule s) {
        cancel(s.getId());
        if (s.getEnabled() == null || s.getEnabled() != 1) {
            return;
        }
        try {
            CronTrigger trigger = new CronTrigger(s.getCron());
            ScheduledFuture<?> future = taskScheduler.schedule(
                    () -> runOnce(s.getId()), trigger);
            tasks.put(s.getId(), future);
        } catch (IllegalArgumentException e) {
            log.warn("定时任务 {} cron 非法: {}", s.getId(), s.getCron());
        }
    }

    /** 取消 */
    public void cancel(Long id) {
        ScheduledFuture<?> f = tasks.remove(id);
        if (f != null) {
            f.cancel(false);
        }
    }

    /** 执行一次调度任务：跑场景 → 回写 last_run_time/passed/cost，返回结果。供 cron 触发与「立即运行」复用。 */
    public ScenarioRunResult runOnce(Long scheduleId) {
        TestSchedule s = scheduleMapper.selectById(scheduleId);
        if (s == null) {
            return null;
        }
        try {
            ScenarioRunResult result = scenarioService.runScenario(s.getScenarioId(), s.getEnvId());
            s.setLastRunTime(LocalDateTime.now());
            s.setLastPassed(result.getPassed() ? 1 : 0);
            s.setLastCostMs(result.getCostMs());
            scheduleMapper.updateById(s);
            log.info("定时任务 {} 执行完成: scenario={}, passed={}", scheduleId, s.getScenarioId(), result.getPassed());
            return result;
        } catch (Exception e) {
            log.error("定时任务 {} 执行异常: {}", scheduleId, e.getMessage(), e);
            s.setLastRunTime(LocalDateTime.now());
            s.setLastPassed(0);
            scheduleMapper.updateById(s);
            throw new RuntimeException(e);
        }
    }
}
