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
import com.miragemock.common.exception.BizException;
import com.miragemock.core.cache.RuleCache;
import com.miragemock.admin.security.AuthContext;
import com.miragemock.admin.dto.MemberRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper memberMapper;
    private final ApiInterfaceMapper interfaceMapper;
    private final MockRuleMapper ruleMapper;
    private final RuleCache ruleCache;

    @Autowired
    public ProjectService(ProjectMapper projectMapper, ProjectMemberMapper memberMapper,
                          ApiInterfaceMapper interfaceMapper, MockRuleMapper ruleMapper, RuleCache ruleCache) {
        this.projectMapper = projectMapper;
        this.memberMapper = memberMapper;
        this.interfaceMapper = interfaceMapper;
        this.ruleMapper = ruleMapper;
        this.ruleCache = ruleCache;
    }

    public List<Project> list() {
        return projectMapper.selectList(new LambdaQueryWrapper<Project>().orderByDesc(Project::getCreateTime));
    }

    public Project get(Long id) {
        Project p = projectMapper.selectById(id);
        if (p == null) {
            throw new BizException(ResultCode.PROJECT_NOT_FOUND);
        }
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
        // 级联清理接口与规则
        List<ApiInterface> interfaces = interfaceMapper.selectList(
                new LambdaQueryWrapper<ApiInterface>().eq(ApiInterface::getProjectId, id));
        for (ApiInterface iface : interfaces) {
            ruleMapper.delete(new LambdaQueryWrapper<MockRule>().eq(MockRule::getInterfaceId, iface.getId()));
        }
        interfaceMapper.delete(new LambdaQueryWrapper<ApiInterface>().eq(ApiInterface::getProjectId, id));
        memberMapper.delete(new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getProjectId, id));
        projectMapper.deleteById(id);
        ruleCache.reloadAll();
    }

    public List<ProjectMember> members(Long projectId) {
        return memberMapper.selectList(new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getProjectId, projectId));
    }

    @Transactional
    public void addMember(Long projectId, MemberRequest req) {
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
        memberMapper.delete(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, userId));
    }
}
