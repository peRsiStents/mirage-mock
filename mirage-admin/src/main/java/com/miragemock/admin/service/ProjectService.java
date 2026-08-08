package com.miragemock.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.miragemock.admin.mapper.ApiInterfaceMapper;
import com.miragemock.admin.mapper.MockRuleMapper;
import com.miragemock.admin.mapper.ProjectMapper;
import com.miragemock.admin.mapper.ProjectMemberMapper;
import com.miragemock.common.api.ResultCode;
import com.miragemock.common.constant.Constants;
import com.miragemock.common.entity.ApiInterface;
import com.miragemock.common.entity.MockRule;
import com.miragemock.common.entity.Project;
import com.miragemock.common.entity.ProjectMember;
import com.miragemock.common.entity.TcpListener;
import com.miragemock.common.entity.TestSchedule;
import com.miragemock.common.exception.BizException;
import com.miragemock.core.cache.RuleCache;
import com.miragemock.admin.security.AuthContext;
import com.miragemock.admin.security.ProjectAuthz;
import com.miragemock.admin.dto.MemberRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper memberMapper;
    private final ApiInterfaceMapper interfaceMapper;
    private final MockRuleMapper ruleMapper;
    private final RuleCache ruleCache;
    private final ProjectAuthz authz;
    private final TcpListenerService tcpListenerService;
    private final ScheduleService scheduleService;
    private final ProjectDataCleaner cascadeCleaner;

    @Autowired
    public ProjectService(ProjectMapper projectMapper, ProjectMemberMapper memberMapper,
                          ApiInterfaceMapper interfaceMapper, MockRuleMapper ruleMapper, RuleCache ruleCache,
                          ProjectAuthz authz, TcpListenerService tcpListenerService, ScheduleService scheduleService,
                          ProjectDataCleaner cascadeCleaner) {
        this.projectMapper = projectMapper;
        this.memberMapper = memberMapper;
        this.interfaceMapper = interfaceMapper;
        this.ruleMapper = ruleMapper;
        this.ruleCache = ruleCache;
        this.authz = authz;
        this.tcpListenerService = tcpListenerService;
        this.scheduleService = scheduleService;
        this.cascadeCleaner = cascadeCleaner;
    }

    public List<Project> list() {
        // admin 可见全部；普通用户仅可见自己所在项目
        if (AuthContext.isAdmin()) {
            return projectMapper.selectList(new LambdaQueryWrapper<Project>().orderByDesc(Project::getCreateTime));
        }
        Long uid = AuthContext.currentUserId();
        if (uid == null) {
            return Collections.emptyList();
        }
        List<ProjectMember> ms = memberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getUserId, uid));
        if (ms.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> ids = new HashSet<>();
        for (ProjectMember m : ms) {
            ids.add(m.getProjectId());
        }
        return projectMapper.selectList(new LambdaQueryWrapper<Project>()
                .in(Project::getId, ids).orderByDesc(Project::getCreateTime));
    }

    public Project get(Long id) {
        Project p = projectMapper.selectById(id);
        if (p == null) {
            throw new BizException(ResultCode.PROJECT_NOT_FOUND);
        }
        authz.requireMember(id);
        return p;
    }

    @Transactional
    public Project create(Project project) {
        if (project.getCode() == null || project.getCode().isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "项目编码不能为空");
        }
        Long exist = projectMapper.selectCount(new LambdaQueryWrapper<Project>().eq(Project::getCode, project.getCode()));
        if (exist != null && exist > 0) {
            throw new BizException(ResultCode.CONFLICT, "项目编码已存在: " + project.getCode());
        }
        if (project.getStatus() == null) {
            project.setStatus(Constants.STATUS_ENABLED);
        }
        project.setRuleVersion(Constants.INITIAL_RULE_VERSION);
        projectMapper.insert(project);
        // 创建者作为项目管理员
        Long uid = AuthContext.currentUserId();
        if (uid != null) {
            ProjectMember m = new ProjectMember();
            m.setProjectId(project.getId());
            m.setUserId(uid);
            m.setMemberRole("ADMIN");
            memberMapper.insert(m);
        }
        ruleCache.reloadAll();
        return project;
    }

    @Transactional
    public Project update(Long id, Project project) {
        authz.requireAdmin(id);
        Project exists = get(id);
        if (project.getName() != null) {
            exists.setName(project.getName());
        }
        if (project.getRemark() != null) {
            exists.setRemark(project.getRemark());
        }
        if (project.getStatus() != null) {
            exists.setStatus(project.getStatus());
        }
        projectMapper.updateById(exists);
        ruleCache.invalidate(id);
        return exists;
    }

    @Transactional
    public void delete(Long id) {
        authz.requireAdmin(id);
        // 先停运行时副作用：TCP 监听线程、定时任务
        for (TcpListener l : tcpListenerService.list(id)) {
            tcpListenerService.delete(l.getId());
        }
        for (TestSchedule s : scheduleService.list(id)) {
            scheduleService.delete(s.getId());
        }
        // 再清所有子表数据（接口/规则/成员/用例/场景/步骤/环境/变量/日志/记录/密钥/序列/文件模板）
        cascadeCleaner.deleteProjectData(id);
        projectMapper.deleteById(id);
        ruleCache.reloadAll();
    }

    public List<ProjectMember> members(Long projectId) {
        authz.requireMember(projectId);
        return memberMapper.selectList(new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getProjectId, projectId));
    }

    @Transactional
    public void addMember(Long projectId, MemberRequest req) {
        authz.requireAdmin(projectId);
        get(projectId);
        Long count = memberMapper.selectCount(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, req.getUserId()));
        if (count != null && count > 0) {
            throw new BizException(ResultCode.CONFLICT, "该用户已是项目成员");
        }
        ProjectMember m = new ProjectMember();
        m.setProjectId(projectId);
        m.setUserId(req.getUserId());
        m.setMemberRole(req.getMemberRole() == null ? "MEMBER" : req.getMemberRole());
        memberMapper.insert(m);
    }

    @Transactional
    public void removeMember(Long projectId, Long userId) {
        authz.requireAdmin(projectId);
        memberMapper.delete(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, userId));
    }

    /**
     * 一键生成示例项目：含 2 个 HTTP 接口与若干 Mock 规则（兜底、条件命中、随机延迟），
     * 供新用户立即体验「配接口 → 发请求 → 命中规则」闭环。可随时删除。
     */
    @Transactional
    public Project createSample() {
        String code = uniqueSampleCode();
        Project p = new Project();
        p.setName("示例项目");
        p.setCode(code);
        p.setRemark("系统自动生成的示例（HTTP 接口 + Mock 规则 + 延迟演示），可随时删除");
        p.setStatus(Constants.STATUS_ENABLED);
        p.setRuleVersion(Constants.INITIAL_RULE_VERSION);
        projectMapper.insert(p);

        Long uid = AuthContext.currentUserId();
        if (uid != null) {
            ProjectMember m = new ProjectMember();
            m.setProjectId(p.getId());
            m.setUserId(uid);
            m.setMemberRole("ADMIN");
            memberMapper.insert(m);
        }

        // 接口1：查询用户（GET，演示条件命中 vs 兜底）
        ApiInterface userApi = newInterface(p.getId(), "查询用户", "GET", "/api/" + code + "/user");
        addRule(userApi.getId(), "U001 专属", 100,
                "[{\"source\":\"query\",\"key\":\"userId\",\"op\":\"eq\",\"value\":\"U001\"}]",
                "{\"status\":200,\"body\":{\"id\":\"U001\",\"name\":\"张三\",\"age\":28}}", "NONE", 0, 0);
        addRule(userApi.getId(), "兜底", 999, "[]",
                "{\"status\":200,\"body\":{\"id\":\"?\",\"name\":\"示例用户\"}}", "NONE", 0, 0);

        // 接口2：下单（POST，演示随机延迟 + DSL 函数）
        ApiInterface orderApi = newInterface(p.getId(), "下单", "POST", "/api/" + code + "/order");
        addRule(orderApi.getId(), "兜底", 999, "[]",
                "{\"status\":200,\"body\":{\"code\":\"0000\",\"msg\":\"success\",\"orderId\":\"${uuid()}\"}}",
                "RANDOM", 100, 500);

        ruleCache.reloadAll();
        return p;
    }

    private String uniqueSampleCode() {
        for (int i = 0; i < 1000; i++) {
            String code = i == 0 ? "demo" : "demo" + i;
            Long c = projectMapper.selectCount(new LambdaQueryWrapper<Project>().eq(Project::getCode, code));
            if (c == null || c == 0) {
                return code;
            }
        }
        return "demo" + System.currentTimeMillis();
    }

    private ApiInterface newInterface(Long pid, String name, String method, String path) {
        ApiInterface it = new ApiInterface();
        it.setProjectId(pid);
        it.setName(name);
        it.setProtocol("HTTP");
        it.setHttpMethod(method);
        it.setHttpPath(path);
        it.setStatus(Constants.STATUS_ENABLED);
        interfaceMapper.insert(it);
        return it;
    }

    private void addRule(Long iid, String name, int priority, String match, String template,
                         String delayType, int delayMin, int delayMax) {
        MockRule r = new MockRule();
        r.setInterfaceId(iid);
        r.setName(name);
        r.setPriority(priority);
        r.setMatchCondition(match);
        r.setResponseTemplate(template);
        r.setDelayType(delayType);
        if ("RANDOM".equals(delayType)) {
            r.setDelayMinMs(delayMin);
            r.setDelayMaxMs(delayMax);
        } else {
            r.setDelayMs(delayMin);
        }
        r.setFaultType("NONE");
        r.setStatus(Constants.STATUS_ENABLED);
        ruleMapper.insert(r);
    }
}
