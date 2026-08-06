package com.miragemock.admin.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.miragemock.admin.mapper.ProjectMemberMapper;
import com.miragemock.common.api.ResultCode;
import com.miragemock.common.entity.ProjectMember;
import com.miragemock.common.exception.BizException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 项目级鉴权：校验当前登录用户对某项目是否有访问/管理权限（admin 直通）。
 *
 * <p>用于堵 IDOR：所有项目级与单资源（按 id 直查直改）接口在入口处调用 {@link #requireMember}，
 * 单资源接口需先取出资源的 projectId 再校验。admin 账号直接放行。
 */
@Component
public class ProjectAuthz {

    private final ProjectMemberMapper memberMapper;

    @Autowired
    public ProjectAuthz(ProjectMemberMapper memberMapper) {
        this.memberMapper = memberMapper;
    }

    /** 校验当前用户是项目成员（或 admin）。 */
    public void requireMember(Long projectId) {
        if (projectId == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "缺少项目");
        }
        if (AuthContext.isAdmin()) {
            return;
        }
        Long uid = AuthContext.currentUserId();
        if (uid == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        Long c = memberMapper.selectCount(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, uid));
        if (c == null || c == 0) {
            throw new BizException(ResultCode.FORBIDDEN, "无权访问该项目");
        }
    }

    /** 校验当前用户是项目管理员（或 admin）。 */
    public void requireAdmin(Long projectId) {
        if (projectId == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "缺少项目");
        }
        if (AuthContext.isAdmin()) {
            return;
        }
        Long uid = AuthContext.currentUserId();
        if (uid == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        Long c = memberMapper.selectCount(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, uid)
                .eq(ProjectMember::getMemberRole, "ADMIN"));
        if (c == null || c == 0) {
            throw new BizException(ResultCode.FORBIDDEN, "需要项目管理员权限");
        }
    }
}
