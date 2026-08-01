package com.miragemock.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.miragemock.admin.dto.ScenarioRunResult;
import com.miragemock.admin.mapper.TestScheduleMapper;
import com.miragemock.admin.runtime.ScheduleManager;
import com.miragemock.common.api.ResultCode;
import com.miragemock.common.constant.Constants;
import com.miragemock.common.entity.TestSchedule;
import com.miragemock.common.exception.BizException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 定时任务管理：CRUD + cron 校验 + 委托 ScheduleManager 注册/注销 + 立即运行 */
@Service
public class ScheduleService {

    private final TestScheduleMapper scheduleMapper;
    private final ScheduleManager scheduleManager;
    private final ScenarioService scenarioService;

    @Autowired
    public ScheduleService(TestScheduleMapper scheduleMapper, ScheduleManager scheduleManager,
                           ScenarioService scenarioService) {
        this.scheduleMapper = scheduleMapper;
        this.scheduleManager = scheduleManager;
        this.scenarioService = scenarioService;
    }

    public List<TestSchedule> list(Long projectId) {
        return scheduleMapper.selectList(new LambdaQueryWrapper<TestSchedule>()
                .eq(TestSchedule::getProjectId, projectId)
                .orderByDesc(TestSchedule::getCreateTime));
    }

    public TestSchedule get(Long id) {
        TestSchedule s = scheduleMapper.selectById(id);
        if (s == null) {
            throw new BizException(ResultCode.NOT_FOUND, "定时任务不存在");
        }
        return s;
    }

    @Transactional
    public TestSchedule create(TestSchedule s) {
        if (s.getProjectId() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "projectId 不能为空");
        }
        validateCron(s.getCron());
        if (s.getEnabled() == null) s.setEnabled(Constants.STATUS_ENABLED);
        scheduleMapper.insert(s);
        scheduleManager.register(s);
        return s;
    }

    @Transactional
    public TestSchedule update(Long id, TestSchedule patch) {
        TestSchedule exists = get(id);
        if (patch.getName() != null) exists.setName(patch.getName());
        if (patch.getScenarioId() != null) exists.setScenarioId(patch.getScenarioId());
        if (patch.getCron() != null) {
            validateCron(patch.getCron());
            exists.setCron(patch.getCron());
        }
        if (patch.getEnvId() != null) exists.setEnvId(patch.getEnvId());
        if (patch.getRemark() != null) exists.setRemark(patch.getRemark());
        if (patch.getEnabled() != null) exists.setEnabled(patch.getEnabled());
        scheduleMapper.updateById(exists);
        scheduleManager.register(exists);
        return exists;
    }

    @Transactional
    public void delete(Long id) {
        get(id);
        scheduleManager.cancel(id);
        scheduleMapper.deleteById(id);
    }

    @Transactional
    public TestSchedule toggle(Long id) {
        TestSchedule s = get(id);
        s.setEnabled(s.getEnabled() != null && s.getEnabled() == 1 ? Constants.STATUS_DISABLED : Constants.STATUS_ENABLED);
        scheduleMapper.updateById(s);
        if (s.getEnabled() != null && s.getEnabled() == 1) {
            scheduleManager.register(s);
        } else {
            scheduleManager.cancel(id);
        }
        return s;
    }

    public ScenarioRunResult runNow(Long id) {
        // 委托 ScheduleManager.runOnce：跑场景并回写 last_run_time/passed/cost
        get(id);
        return scheduleManager.runOnce(id);
    }

    /** 校验 cron 并返回接下来 count 次触发时间（与调度器同源 CronExpression）。非法抛 BizException。 */
    public List<LocalDateTime> cronPreview(String cron, int count) {
        if (cron == null || cron.trim().isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "cron 不能为空");
        }
        CronExpression expr;
        try {
            expr = CronExpression.parse(cron.trim());
        } catch (IllegalArgumentException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "cron 表达式非法: " + e.getMessage());
        }
        int n = (count <= 0 || count > 10) ? 3 : count;
        List<LocalDateTime> out = new ArrayList<>();
        LocalDateTime t = LocalDateTime.now();
        for (int i = 0; i < n; i++) {
            t = expr.next(t);
            if (t == null) {
                break;
            }
            out.add(t);
        }
        return out;
    }

    private void validateCron(String cron) {
        if (cron == null || cron.trim().isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "cron 不能为空");
        }
        try {
            new CronTrigger(cron);
        } catch (IllegalArgumentException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "cron 表达式非法: " + e.getMessage());
        }
    }
}
