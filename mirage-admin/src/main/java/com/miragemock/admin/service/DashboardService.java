package com.miragemock.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.miragemock.admin.mapper.ApiInterfaceMapper;
import com.miragemock.admin.mapper.MockRequestLogMapper;
import com.miragemock.admin.mapper.MockRuleMapper;
import com.miragemock.admin.mapper.TcpListenerMapper;
import com.miragemock.admin.mapper.TestCaseMapper;
import com.miragemock.admin.mapper.TestRunLogMapper;
import com.miragemock.admin.mapper.TestScheduleMapper;
import com.miragemock.admin.mapper.TestScenarioMapper;
import com.miragemock.common.entity.ApiInterface;
import com.miragemock.common.entity.MockRequestLog;
import com.miragemock.common.entity.MockRule;
import com.miragemock.common.entity.TcpListener;
import com.miragemock.common.entity.TestCase;
import com.miragemock.common.entity.TestRunLog;
import com.miragemock.common.entity.TestSchedule;
import com.miragemock.common.entity.TestScenario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工作台首页聚合统计：按项目汇总接口/规则/监听器/用例/场景/定时任务数量、
 * Mock 请求量、测试通过率，以及最近活动，供首页 Dashboard 渲染。
 */
@Service
public class DashboardService {

    private static final int RECENT_LIMIT = 5;

    private final ApiInterfaceMapper interfaceMapper;
    private final MockRuleMapper ruleMapper;
    private final TcpListenerMapper listenerMapper;
    private final MockRequestLogMapper logMapper;
    private final TestCaseMapper caseMapper;
    private final TestRunLogMapper runLogMapper;
    private final TestScheduleMapper scheduleMapper;
    private final TestScenarioMapper scenarioMapper;

    @Autowired
    public DashboardService(ApiInterfaceMapper interfaceMapper, MockRuleMapper ruleMapper,
                            TcpListenerMapper listenerMapper, MockRequestLogMapper logMapper,
                            TestCaseMapper caseMapper, TestRunLogMapper runLogMapper,
                            TestScheduleMapper scheduleMapper, TestScenarioMapper scenarioMapper) {
        this.interfaceMapper = interfaceMapper;
        this.ruleMapper = ruleMapper;
        this.listenerMapper = listenerMapper;
        this.logMapper = logMapper;
        this.caseMapper = caseMapper;
        this.runLogMapper = runLogMapper;
        this.scheduleMapper = scheduleMapper;
        this.scenarioMapper = scenarioMapper;
    }

    public Map<String, Object> overview(Long projectId) {
        Map<String, Object> data = new LinkedHashMap<>();

        // —— 资源数量 ——
        data.put("interfaceCount", interfaceMapper.selectCount(
                new LambdaQueryWrapper<ApiInterface>().eq(ApiInterface::getProjectId, projectId)));
        data.put("listenerCount", listenerMapper.selectCount(
                new LambdaQueryWrapper<TcpListener>().eq(TcpListener::getProjectId, projectId)));
        data.put("ruleCount", countRules(projectId));
        data.put("caseCount", caseMapper.selectCount(
                new LambdaQueryWrapper<TestCase>().eq(TestCase::getProjectId, projectId)));
        data.put("scenarioCount", scenarioMapper.selectCount(
                new LambdaQueryWrapper<TestScenario>().eq(TestScenario::getProjectId, projectId)));

        // —— Mock 流量 ——
        data.put("logTotal", logMapper.selectCount(
                new LambdaQueryWrapper<MockRequestLog>().eq(MockRequestLog::getProjectId, projectId)));
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        data.put("logToday", logMapper.selectCount(
                new LambdaQueryWrapper<MockRequestLog>()
                        .eq(MockRequestLog::getProjectId, projectId)
                        .ge(MockRequestLog::getCreateTime, todayStart)));

        // —— 测试通过率 ——
        Long runTotal = runLogMapper.selectCount(
                new LambdaQueryWrapper<TestRunLog>().eq(TestRunLog::getProjectId, projectId));
        Long runPassed = runLogMapper.selectCount(
                new LambdaQueryWrapper<TestRunLog>()
                        .eq(TestRunLog::getProjectId, projectId)
                        .eq(TestRunLog::getPassed, 1));
        data.put("runTotal", runTotal);
        data.put("runPassed", runPassed);
        data.put("runPassRate", runTotal == 0 ? 0d : Math.round(runPassed * 10000.0 / runTotal) / 100.0);

        // —— 定时任务健康度 ——
        data.put("scheduleCount", scheduleMapper.selectCount(
                new LambdaQueryWrapper<TestSchedule>().eq(TestSchedule::getProjectId, projectId)));
        data.put("scheduleFailCount", scheduleMapper.selectCount(
                new LambdaQueryWrapper<TestSchedule>()
                        .eq(TestSchedule::getProjectId, projectId)
                        .eq(TestSchedule::getLastPassed, 0)));

        // —— 最近活动 ——
        data.put("recentLogs", recentLogs(projectId));
        data.put("recentRuns", recentRuns(projectId));

        return data;
    }

    /** 规则按 interface_id 归属，需先取项目接口 id 再 in 查询 */
    private Long countRules(Long projectId) {
        List<ApiInterface> ifaces = interfaceMapper.selectList(
                new LambdaQueryWrapper<ApiInterface>()
                        .eq(ApiInterface::getProjectId, projectId)
                        .select(ApiInterface::getId));
        if (ifaces.isEmpty()) {
            return 0L;
        }
        Set<Long> ids = new HashSet<>();
        for (ApiInterface it : ifaces) {
            ids.add(it.getId());
        }
        return ruleMapper.selectCount(new LambdaQueryWrapper<MockRule>().in(MockRule::getInterfaceId, ids));
    }

    private List<Map<String, Object>> recentLogs(Long projectId) {
        List<MockRequestLog> logs = logMapper.selectList(
                new LambdaQueryWrapper<MockRequestLog>()
                        .eq(MockRequestLog::getProjectId, projectId)
                        .orderByDesc(MockRequestLog::getCreateTime)
                        .last("LIMIT " + RECENT_LIMIT));
        List<Map<String, Object>> out = new ArrayList<>(logs.size());
        for (MockRequestLog l : logs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.getId());
            m.put("protocol", l.getProtocol());
            m.put("clientAddr", l.getClientAddr());
            m.put("matched", l.getMatched());
            m.put("costMs", l.getCostMs());
            m.put("createTime", l.getCreateTime());
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> recentRuns(Long projectId) {
        List<TestRunLog> runs = runLogMapper.selectList(
                new LambdaQueryWrapper<TestRunLog>()
                        .eq(TestRunLog::getProjectId, projectId)
                        .orderByDesc(TestRunLog::getCreateTime)
                        .last("LIMIT " + RECENT_LIMIT));
        List<Map<String, Object>> out = new ArrayList<>(runs.size());
        for (TestRunLog r : runs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("caseId", r.getCaseId());
            m.put("passed", r.getPassed());
            m.put("httpStatus", r.getHttpStatus());
            m.put("costMs", r.getCostMs());
            m.put("error", r.getError());
            m.put("createTime", r.getCreateTime());
            out.add(m);
        }
        return out;
    }
}
