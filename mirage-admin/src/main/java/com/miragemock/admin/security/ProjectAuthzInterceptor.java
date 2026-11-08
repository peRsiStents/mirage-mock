package com.miragemock.admin.security;

import com.miragemock.common.api.ResultCode;
import com.miragemock.common.exception.BizException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * 按 URL 中的 {pid} 路径变量统一校验项目成员权限（admin 直通）。
 *
 * <p>覆盖所有 {@code /api/v1/projects/{pid}/...} 路由（各资源的 列表/创建/范围查询），
 * 单资源按 id 的写/读操作由各 service 自行解析 projectId 后调用 {@link ProjectAuthz}。
 * 在 JwtAuthFilter 之后执行，AuthContext 已就绪。
 */
public class ProjectAuthzInterceptor implements HandlerInterceptor {

    private final ProjectAuthz authz;

    public ProjectAuthzInterceptor(ProjectAuthz authz) {
        this.authz = authz;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Object attr = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (attr instanceof Map) {
            Object pid = ((Map<?, ?>) attr).get("pid");
            if (pid != null && !"".equals(pid)) {
                // 非数字 pid 是非法请求（400），不能抛裸 NumberFormatException（500）
                long projectId;
                try {
                    projectId = Long.parseLong(String.valueOf(pid).trim());
                } catch (NumberFormatException e) {
                    throw new BizException(ResultCode.BAD_REQUEST, "项目 ID 非法: " + pid);
                }
                // requireMember 抛 BizException(FORBIDDEN) 由全局异常处理转 JSON
                authz.requireMember(projectId);
            }
        }
        return true;
    }
}
