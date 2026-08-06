package com.miragemock.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.miragemock.admin.dto.RunResult;
import com.miragemock.admin.dto.ScenarioRunResult;
import com.miragemock.admin.mapper.TestCaseMapper;
import com.miragemock.admin.mapper.TestRunRecordMapper;
import com.miragemock.admin.mapper.TestScenarioMapper;
import com.miragemock.admin.mapper.TestScenarioStepMapper;
import com.miragemock.admin.security.ProjectAuthz;
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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final ProjectAuthz authz;

    @Autowired
    public ScenarioService(TestScenarioMapper scenarioMapper, TestScenarioStepMapper stepMapper,
                           TestRunRecordMapper recordMapper, TestCaseMapper caseMapper,
                           TestCaseService testCaseService, ProjectAuthz authz) {
        this.scenarioMapper = scenarioMapper;
        this.stepMapper = stepMapper;
        this.recordMapper = recordMapper;
        this.caseMapper = caseMapper;
        this.testCaseService = testCaseService;
        this.authz = authz;
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
        authz.requireMember(s.getProjectId());
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
        // 直接取场景，不走 get()（后者带成员校验）：本方法也被 CI(token 鉴权)与定时调度(系统线程)调用，无 AuthContext
        TestScenario sc = scenarioMapper.selectById(scenarioId);
        if (sc == null) {
            throw new BizException(ResultCode.NOT_FOUND, "测试场景不存在");
        }
        Long env = envId != null ? envId : sc.getEnvId();
        Long projectId = sc.getProjectId();
        String onFail = sc.getOnFail() == null ? "STOP" : sc.getOnFail().toUpperCase();

        List<TestScenarioStep> steps = stepMapper.selectList(new LambdaQueryWrapper<TestScenarioStep>()
                .eq(TestScenarioStep::getScenarioId, scenarioId)
                .eq(TestScenarioStep::getEnabled, 1)
                .orderByAsc(TestScenarioStep::getSeq));

        // 批量预取步骤用例，避免逐步 selectById（N+1）；按 id 索引复用。
        Set<Long> caseIdSet = new HashSet<>();
        for (TestScenarioStep step : steps) {
            if (step.getCaseId() != null) {
                caseIdSet.add(step.getCaseId());
            }
        }
        Map<Long, TestCase> caseMap = new HashMap<>();
        if (!caseIdSet.isEmpty()) {
            for (TestCase c : caseMapper.selectBatchIds(caseIdSet)) {
                if (c.getId() != null) {
                    caseMap.put(c.getId(), c);
                }
            }
        }
        // 项目变量 + 环境变量只加载一次，多步骤/多数据行复用（原 runOnce 每次都查两轮库）。
        Map<String, Object> baseVars = testCaseService.buildBaseVars(projectId, env);

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
            TestCase tc = caseMap.get(step.getCaseId());
            d.put("caseName", tc == null ? "(用例已删除)" : tc.getName());

            if (stopped) {
                d.put("skipped", true);
                d.put("passed", false);
                detail.add(d);
                continue;
            }
            total++;
            List<Map<String, Object>> rows = parseList(tc == null ? null : tc.getDataSet());
            boolean stepPassed;
            if (tc != null && !rows.isEmpty()) {
                // 数据驱动：按数据行逐行执行（每行注入 ${var.<k>}），步骤通过=全行通过
                d.put("dataDriven", true);
                List<Map<String, Object>> rowResults = new ArrayList<>();
                Map<String, Object> stepExtracts = new LinkedHashMap<>();
                long stepCost = 0;
                int lastStatus = 0;
                String lastError = null;
                stepPassed = true;
                for (int i = 0; i < rows.size(); i++) {
                    Map<String, Object> rowExtra = new HashMap<>(runtimeExtra);
                    for (Map.Entry<String, Object> en : rows.get(i).entrySet()) {
                        if (en.getKey() != null && !en.getKey().isEmpty()) {
                            rowExtra.put("var." + en.getKey(), en.getValue());
                        }
                    }
                    RunResult rr = runOnce(tc, projectId, baseVars, rowExtra);
                    Map<String, Object> ex = testCaseService.extract(rr, parseList(step.getExtract()));
                    runtimeExtra.putAll(ex);
                    stepExtracts.putAll(ex);
                    stepCost += rr.getCostMs() == null ? 0 : rr.getCostMs();
                    lastStatus = rr.getHttpStatus() == null ? 0 : rr.getHttpStatus();
                    lastError = rr.getError();
                    if (!Boolean.TRUE.equals(rr.getPassed())) {
                        stepPassed = false;
                    }
                    Map<String, Object> rd = new LinkedHashMap<>();
                    rd.put("row", i + 1);
                    rd.put("vars", rows.get(i));
                    rd.put("passed", rr.getPassed());
                    rd.put("httpStatus", rr.getHttpStatus());
                    rd.put("costMs", rr.getCostMs());
                    rd.put("error", rr.getError());
                    rd.put("body", truncate(rr.getBody()));
                    rd.put("assertions", rr.getAssertions());
                    rowResults.add(rd);
                }
                d.put("rows", rowResults);
                d.put("passed", stepPassed);
                d.put("httpStatus", lastStatus);
                d.put("costMs", stepCost);
                d.put("error", stepPassed ? null : (lastError != null ? lastError : "存在失败数据行"));
                d.put("extracts", stepExtracts);
                d.put("skipped", false);
            } else {
                // 普通单次执行
                RunResult rr = runOnce(tc, projectId, baseVars, runtimeExtra);
                Map<String, Object> extracts = testCaseService.extract(rr, parseList(step.getExtract()));
                runtimeExtra.putAll(extracts);
                stepPassed = Boolean.TRUE.equals(rr.getPassed());
                d.put("passed", rr.getPassed());
                d.put("httpStatus", rr.getHttpStatus());
                d.put("costMs", rr.getCostMs());
                d.put("error", rr.getError());
                d.put("headers", rr.getHeaders());
                d.put("body", truncate(rr.getBody()));
                d.put("assertions", rr.getAssertions());
                d.put("extracts", extracts);
                d.put("skipped", false);
            }

            if (stepPassed) {
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

    public PageResult<TestRunRecord> records(Long projectId, String type, Long targetId, Integer passed,
                                             Long from, Long to, long page, long size) {
        if (page < 1) page = 1;
        if (size < 1 || size > 200) size = 20;
        LambdaQueryWrapper<TestRunRecord> w = new LambdaQueryWrapper<TestRunRecord>()
                // 列表瘦身：排除大字段 detail（每步明细 JSON），详情弹窗经 record(id) 懒加载
                .select(TestRunRecord::getId, TestRunRecord::getProjectId, TestRunRecord::getTargetType,
                        TestRunRecord::getTargetId, TestRunRecord::getEnvId, TestRunRecord::getPassed,
                        TestRunRecord::getTotalSteps, TestRunRecord::getPassedSteps, TestRunRecord::getFailedSteps,
                        TestRunRecord::getCostMs, TestRunRecord::getCreateTime, TestRunRecord::getUpdateTime)
                .eq(TestRunRecord::getProjectId, projectId)
                .orderByDesc(TestRunRecord::getCreateTime);
        if (type != null && !type.isEmpty()) w.eq(TestRunRecord::getTargetType, type);
        if (targetId != null) w.eq(TestRunRecord::getTargetId, targetId);
        if (passed != null) w.eq(TestRunRecord::getPassed, passed);
        if (from != null) w.ge(TestRunRecord::getCreateTime, new Date(from).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        if (to != null) w.le(TestRunRecord::getCreateTime, new Date(to).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        Page<TestRunRecord> p = recordMapper.selectPage(new Page<>(page, size), w);
        enrichTargetNames(p.getRecords());
        return PageResult.of(p.getRecords(), p.getTotal(), page, size);
    }

    /** 回填每条记录对应的场景名 / 用例名，供报告列表「名称」列展示。 */
    private void enrichTargetNames(List<TestRunRecord> recs) {
        if (recs == null || recs.isEmpty()) {
            return;
        }
        Set<Long> scIds = new HashSet<>();
        Set<Long> caseIds = new HashSet<>();
        for (TestRunRecord r : recs) {
            if (r.getTargetId() == null) {
                continue;
            }
            if ("scenario".equals(r.getTargetType())) {
                scIds.add(r.getTargetId());
            } else {
                caseIds.add(r.getTargetId());
            }
        }
        Map<Long, String> scNames = new HashMap<>();
        if (!scIds.isEmpty()) {
            for (TestScenario s : scenarioMapper.selectBatchIds(scIds)) {
                if (s.getId() != null) {
                    scNames.put(s.getId(), s.getName());
                }
            }
        }
        Map<Long, String> caseNames = new HashMap<>();
        if (!caseIds.isEmpty()) {
            for (TestCase c : caseMapper.selectBatchIds(caseIds)) {
                if (c.getId() != null) {
                    caseNames.put(c.getId(), c.getName());
                }
            }
        }
        for (TestRunRecord r : recs) {
            r.setTargetName("scenario".equals(r.getTargetType()) ? scNames.get(r.getTargetId()) : caseNames.get(r.getTargetId()));
        }
    }

    public TestRunRecord record(Long id) {
        TestRunRecord r = recordMapper.selectById(id);
        if (r == null) {
            throw new BizException(ResultCode.NOT_FOUND, "报告不存在");
        }
        authz.requireMember(r.getProjectId());
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

    /** 执行单个用例一次（基于已加载的 baseVars 注入运行时变量），失败转成 RunResult 而不抛出。 */
    private RunResult runOnce(TestCase tc, Long projectId, Map<String, Object> baseVars, Map<String, Object> extraVars) {
        if (tc == null) {
            RunResult rr = new RunResult();
            rr.setAssertions(new ArrayList<>());
            rr.setPassed(false);
            rr.setError("用例已删除");
            return rr;
        }
        try {
            Map<String, Object> vars = new HashMap<>(baseVars);
            if (extraVars != null) {
                vars.putAll(extraVars);
            }
            return testCaseService.executeCase(tc, testCaseService.newContext(projectId, vars));
        } catch (Exception e) {
            RunResult rr = new RunResult();
            rr.setAssertions(new ArrayList<>());
            rr.setPassed(false);
            rr.setError("执行异常: " + e.getMessage());
            return rr;
        }
    }

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
