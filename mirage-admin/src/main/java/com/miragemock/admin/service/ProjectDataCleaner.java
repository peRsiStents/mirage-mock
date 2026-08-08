package com.miragemock.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.miragemock.admin.mapper.ApiInterfaceMapper;
import com.miragemock.admin.mapper.FileTemplateMapper;
import com.miragemock.admin.mapper.MockRequestLogMapper;
import com.miragemock.admin.mapper.MockRuleMapper;
import com.miragemock.admin.mapper.MockSequenceMapper;
import com.miragemock.admin.mapper.ProjectMemberMapper;
import com.miragemock.admin.mapper.SecretKeyMapper;
import com.miragemock.admin.mapper.TestCaseMapper;
import com.miragemock.admin.mapper.TestEnvironmentMapper;
import com.miragemock.admin.mapper.TestRunLogMapper;
import com.miragemock.admin.mapper.TestRunRecordMapper;
import com.miragemock.admin.mapper.TestScenarioMapper;
import com.miragemock.admin.mapper.TestScenarioStepMapper;
import com.miragemock.admin.mapper.TestVariableMapper;
import com.miragemock.common.entity.ApiInterface;
import com.miragemock.common.entity.MockRule;
import com.miragemock.common.entity.ProjectMember;
import com.miragemock.common.entity.TestCase;
import com.miragemock.common.entity.TestEnvironment;
import com.miragemock.common.entity.TestRunLog;
import com.miragemock.common.entity.TestRunRecord;
import com.miragemock.common.entity.TestScenario;
import com.miragemock.common.entity.TestScenarioStep;
import com.miragemock.common.entity.TestVariable;
import com.miragemock.common.entity.SecretKey;
import com.miragemock.common.entity.MockSequence;
import com.miragemock.common.entity.FileTemplate;
import com.miragemock.common.entity.MockRequestLog;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 项目级联清理：删除项目时清掉其下所有子表数据，避免多用户场景下残留孤儿数据。
 * 只依赖 Mapper（无 Service 依赖），故不会卷入 TestCaseService↔ScenarioService 的依赖环；
 * 带运行时副作用的 TCP 监听器/定时任务由 ProjectService 另行调用对应 Service 停机后再到这里清表。
 */
@Component
public class ProjectDataCleaner {

    private final TestCaseMapper caseMapper;
    private final TestScenarioMapper scenarioMapper;
    private final TestScenarioStepMapper stepMapper;
    private final TestEnvironmentMapper environmentMapper;
    private final TestVariableMapper variableMapper;
    private final MockRequestLogMapper requestLogMapper;
    private final TestRunLogMapper runLogMapper;
    private final TestRunRecordMapper runRecordMapper;
    private final SecretKeyMapper secretKeyMapper;
    private final MockSequenceMapper sequenceMapper;
    private final FileTemplateMapper fileTemplateMapper;
    private final ApiInterfaceMapper interfaceMapper;
    private final MockRuleMapper ruleMapper;
    private final ProjectMemberMapper memberMapper;

    public ProjectDataCleaner(TestCaseMapper caseMapper, TestScenarioMapper scenarioMapper,
                              TestScenarioStepMapper stepMapper, TestEnvironmentMapper environmentMapper,
                              TestVariableMapper variableMapper, MockRequestLogMapper requestLogMapper,
                              TestRunLogMapper runLogMapper, TestRunRecordMapper runRecordMapper,
                              SecretKeyMapper secretKeyMapper, MockSequenceMapper sequenceMapper,
                              FileTemplateMapper fileTemplateMapper, ApiInterfaceMapper interfaceMapper,
                              MockRuleMapper ruleMapper, ProjectMemberMapper memberMapper) {
        this.caseMapper = caseMapper;
        this.scenarioMapper = scenarioMapper;
        this.stepMapper = stepMapper;
        this.environmentMapper = environmentMapper;
        this.variableMapper = variableMapper;
        this.requestLogMapper = requestLogMapper;
        this.runLogMapper = runLogMapper;
        this.runRecordMapper = runRecordMapper;
        this.secretKeyMapper = secretKeyMapper;
        this.sequenceMapper = sequenceMapper;
        this.fileTemplateMapper = fileTemplateMapper;
        this.interfaceMapper = interfaceMapper;
        this.ruleMapper = ruleMapper;
        this.memberMapper = memberMapper;
    }

    /**
     * 删除项目下的全部子表数据（不含 tcp_listener / test_schedule 行——这两类带运行时状态，
     * 由 ProjectService 先调 TcpListenerService/ScheduleService 停机并删行）。
     * TCP 接口与规则由 TcpListenerService.delete 逐个清理；此处再按项目删一次兜底残留的 HTTP 接口与规则。
     */
    public void deleteProjectData(Long projectId) {
        // 场景 + 步骤（步骤按 scenarioId 归属，需先收集场景 id 再删步骤）
        List<TestScenario> scenarios = scenarioMapper.selectList(new LambdaQueryWrapper<TestScenario>()
                .eq(TestScenario::getProjectId, projectId));
        for (TestScenario sc : scenarios) {
            stepMapper.delete(new LambdaQueryWrapper<TestScenarioStep>().eq(TestScenarioStep::getScenarioId, sc.getId()));
        }
        scenarioMapper.delete(new LambdaQueryWrapper<TestScenario>().eq(TestScenario::getProjectId, projectId));

        caseMapper.delete(new LambdaQueryWrapper<TestCase>().eq(TestCase::getProjectId, projectId));
        environmentMapper.delete(new LambdaQueryWrapper<TestEnvironment>().eq(TestEnvironment::getProjectId, projectId));
        variableMapper.delete(new LambdaQueryWrapper<TestVariable>().eq(TestVariable::getProjectId, projectId));
        requestLogMapper.delete(new LambdaQueryWrapper<MockRequestLog>().eq(MockRequestLog::getProjectId, projectId));
        runLogMapper.delete(new LambdaQueryWrapper<TestRunLog>().eq(TestRunLog::getProjectId, projectId));
        runRecordMapper.delete(new LambdaQueryWrapper<TestRunRecord>().eq(TestRunRecord::getProjectId, projectId));
        secretKeyMapper.delete(new LambdaQueryWrapper<SecretKey>().eq(SecretKey::getProjectId, projectId));
        sequenceMapper.delete(new LambdaQueryWrapper<MockSequence>().eq(MockSequence::getProjectId, projectId));
        fileTemplateMapper.delete(new LambdaQueryWrapper<FileTemplate>().eq(FileTemplate::getProjectId, projectId));

        // 接口 + 规则（TCP 接口已被 TcpListenerService 清过，这里删剩余 HTTP 接口）
        List<ApiInterface> interfaces = interfaceMapper.selectList(new LambdaQueryWrapper<ApiInterface>()
                .eq(ApiInterface::getProjectId, projectId));
        for (ApiInterface iface : interfaces) {
            ruleMapper.delete(new LambdaQueryWrapper<MockRule>().eq(MockRule::getInterfaceId, iface.getId()));
        }
        interfaceMapper.delete(new LambdaQueryWrapper<ApiInterface>().eq(ApiInterface::getProjectId, projectId));

        memberMapper.delete(new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getProjectId, projectId));
    }
}
