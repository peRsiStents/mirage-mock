package com.miragemock.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.miragemock.admin.dto.RunResult;
import com.miragemock.admin.dto.ScenarioRunResult;
import com.miragemock.admin.mapper.TestCaseMapper;
import com.miragemock.admin.mapper.TestRunRecordMapper;
import com.miragemock.admin.mapper.TestScenarioMapper;
import com.miragemock.admin.mapper.TestScenarioStepMapper;
import com.miragemock.common.api.PageResult;
import com.miragemock.common.api.ResultCode;
import com.miragemock.common.constant.Constants;
import com.miragemock.common.entity.TestCase;
import com.miragemock.common.entity.TestRunRecord;
import com.miragemock.common.entity.TestScenario;
import com.miragemock.common.entity.TestScenarioStep;
import com.miragemock.common.exception.BizException;
import com.miragemock.common.util.JsonUtils;
import com.miragemock.dsl.eval.EvalContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试场景编排：有序步骤链路，步骤间变量提取与传递，失败即停(可配继续)，结果落 test_run_record。
 */
@Service
public class ScenarioService {

    private static final int BODY_TRUNC = 10_000;

    private final TestScenarioMapper scenarioMapper;
    private final TestScenarioStepMapper stepMapper;
    private final TestRunRecordMapper recordMapper;
    private final TestCaseMapper caseMapper;
    private final TestCaseService testCaseService;

    @Autowired
    public ScenarioService(TestScenarioMapper scenarioMapper, TestScenarioStepMapper stepMapper,
                           TestRunRecordMapper recordMapper, TestCaseMapper caseMapper,
                           TestCaseService testCaseService) {
        this.scenarioMapper = scenarioMapper;
        this.stepMapper = stepMapper;
        this.recordMapper = recordMapper;
        this.caseMapper = caseMapper;
        this.testCaseService = testCaseService;
    }

    // ============ 场景 CRUD ============

    public List<TestScenario> list(Long projectId) {
        return scenarioMapper.selectList(new LambdaQueryWrapper<TestScenario>()
                .eq(TestScenario::getProjectId, projectId)
                .orderByDesc(TestScenario::getCreateTime));
    }

    public TestScenario get(Long id) {
        TestScenario s = scenarioMapper.selectById(id);
        if (s == null) {
            throw new BizException(ResultCode.NOT_FOUND, "测试场景不存在");
        }
        return s;
    }

    @Transactional
    public TestScenario create(TestScenario s) {
        if (s.getProjectId() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "projectId 不能为空");
        }
        if (s.getStatus() == null) s.setStatus(Constants.STATUS_ENABLED);
        if (s.getOnFail() == null || s.getOnFail().isEmpty()) s.setOnFail("STOP");
        scenarioMapper.insert(s);
        return s;
    }

    @Transactional
    public TestScenario update(Long id, TestScenario patch) {
        TestScenario exists = get(id);
        if (patch.getName() != null) exists.setName(patch.getName());
        if (patch.getRemark() != null) exists.setRemark(patch.getRemark());
        if (patch.getOnFail() != null) exists.setOnFail(patch.getOnFail());
        if (patch.getEnvId() != null) exists.setEnvId(patch.getEnvId());
        if (patch.getStatus() != null) exists.setStatus(patch.getStatus());
        scenarioMapper.updateById(exists);
        return exists;
    }

    @Transactional
    public void delete(Long id) {
        get(id);
        stepMapper.delete(new LambdaQueryWrapper<TestScenarioStep>().eq(TestScenarioStep::getScenarioId, id));
        scenarioMapper.deleteById(id);
    }

    // ============ 步骤 ============

    public List<TestScenarioStep> steps(Long scenarioId) {
        return stepMapper.selectList(new LambdaQueryWrapper<TestScenarioStep>()
                .eq(TestScenarioStep::getScenarioId, scenarioId)
                .orderByAsc(TestScenarioStep::getSeq));
    }

    /** 整体替换场景步骤（前端按顺序传入，按顺序落库 seq） */
    @Transactional
    public List<TestScenarioStep> saveSteps(Long scenarioId, List<TestScenarioStep> steps) {
        get(scenarioId);
        stepMapper.delete(new LambdaQueryWrapper<TestScenarioStep>().eq(TestScenarioStep::getScenarioId, scenarioId));
        if (steps != null) {
            int seq = 1;
            for (TestScenarioStep st : steps) {
                st.setId(null);
                st.setScenarioId(scenarioId);
                st.setSeq(seq++);
                if (st.getEnabled() == null) st.setEnabled(1);
                if (st.getContinueOnFail() == null) st.setContinueOnFail(0);
                stepMapper.insert(st);
            }
        }
        return steps(scenarioId);
    }

    // ============ 运行 ============

    public ScenarioRunResult runScenario(Long scenarioId, Long envId) {
        TestScenario sc = get(scenarioId);
        Long env = envId != null ? envId : sc.getEnvId();
        Long projectId = sc.getProjectId();
        String onFail = sc.getOnFail() == null ? "STOP" : sc.getOnFail().toUpperCase();

        List<TestScenarioStep> steps = stepMapper.selectList(new LambdaQueryWrapper<TestScenarioStep>()
                .eq(TestScenarioStep::getScenarioId, scenarioId)
                .eq(TestScenarioStep::getEnabled, 1)
                .orderByAsc(TestScenarioStep::getSeq));

        Map<String, Object> runtimeExtra = new HashMap<>();
        List<Map<String, Object>> detail = new ArrayList<>();
        boolean scenarioPassed = true;
        int total = 0, passed = 0, failed = 0;
        boolean stopped = false;
        long t0 = System.currentTimeMillis();

        for (TestScenarioStep step : steps) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("seq", step.getSeq());
            d.put("caseId", step.getCaseId());
            d.put("stepName", step.getName());
            TestCase tc = caseMapper.selectById(step.getCaseId());
            d.put("caseName", tc == null ? "(用例已删除)" : tc.getName());

            if (stopped) {
                d.put("skipped", true);
                d.put("passed", false);
                detail.add(d);
                continue;
            }
            total++;
            RunResult rr;
            try {
                EvalContext ctx = testCaseService.buildEvalContext(projectId, env, runtimeExtra);
                rr = testCaseService.executeCase(tc, ctx);
            } catch (Exception e) {
                rr = new RunResult();
                rr.setAssertions(new ArrayList<>());
                rr.setPassed(false);
                rr.setError("执行异常: " + e.getMessage());
            }
            Map<String, Object> extracts = testCaseService.extract(rr, parseList(step.getExtract()));
            runtimeExtra.putAll(extracts);

            d.put("passed", rr.getPassed());
            d.put("httpStatus", rr.getHttpStatus());
            d.put("costMs", rr.getCostMs());
            d.put("error", rr.getError());
            d.put("headers", rr.getHeaders());
            d.put("body", truncate(rr.getBody()));
            d.put("assertions", rr.getAssertions());
            d.put("extracts", extracts);
            d.put("skipped", false);

            if (Boolean.TRUE.equals(rr.getPassed())) {
                passed++;
            } else {
                failed++;
                scenarioPassed = false;
                if ("STOP".equals(onFail) && !Integer.valueOf(1).equals(step.getContinueOnFail())) {
                    stopped = true;
                }
            }
            detail.add(d);
        }
        long cost = System.currentTimeMillis() - t0;

        TestRunRecord rec = new TestRunRecord();
        rec.setProjectId(projectId);
        rec.setTargetType("scenario");
        rec.setTargetId(scenarioId);
        rec.setEnvId(env);
        rec.setPassed(scenarioPassed ? 1 : 0);
        rec.setTotalSteps(total);
        rec.setPassedSteps(passed);
        rec.setFailedSteps(failed);
        rec.setCostMs(cost);
        rec.setDetail(JsonUtils.toJson(detail));
        recordMapper.insert(rec);

        ScenarioRunResult res = new ScenarioRunResult();
        res.setRecordId(rec.getId());
        res.setPassed(scenarioPassed);
        res.setTotalSteps(total);
        res.setPassedSteps(passed);
        res.setFailedSteps(failed);
        res.setCostMs(cost);
        res.setSteps(detail);
        return res;
    }

    // ============ 报告 ============

    public PageResult<TestRunRecord> records(Long projectId, String type, Long targetId, long page, long size) {
        if (page < 1) page = 1;
        if (size < 1 || size > 200) size = 20;
        LambdaQueryWrapper<TestRunRecord> w = new LambdaQueryWrapper<TestRunRecord>()
                .eq(TestRunRecord::getProjectId, projectId)
                .orderByDesc(TestRunRecord::getCreateTime);
        if (type != null && !type.isEmpty()) w.eq(TestRunRecord::getTargetType, type);
        if (targetId != null) w.eq(TestRunRecord::getTargetId, targetId);
        Page<TestRunRecord> p = recordMapper.selectPage(new Page<>(page, size), w);
        return PageResult.of(p.getRecords(), p.getTotal(), page, size);
    }

    public TestRunRecord record(Long id) {
        TestRunRecord r = recordMapper.selectById(id);
        if (r == null) {
            throw new BizException(ResultCode.NOT_FOUND, "报告不存在");
        }
        return r;
    }

    /** 每天 3:05 清理 30 天前的运行报告 */
    @Scheduled(cron = "0 5 3 * * ?")
    public void cleanup() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(30);
        int deleted = recordMapper.delete(new LambdaQueryWrapper<TestRunRecord>()
                .lt(TestRunRecord::getCreateTime, threshold));
        if (deleted > 0) {
            // 简单日志（不引入 logger，避免类膨胀；用标准错误输出在 cron 中可见）
            System.out.println("[ScenarioService] 清理 30 天前运行报告，删除 " + deleted + " 条");
        }
    }

    // ============ 工具 ============

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseList(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return JsonUtils.fromJson(json, List.class);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String truncate(String s) {
        if (s == null) return null;
        return s.length() > BODY_TRUNC ? s.substring(0, BODY_TRUNC) + "...(截断)" : s;
    }
}
