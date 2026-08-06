package com.miragemock.admin.config;

import com.miragemock.admin.security.ProjectAuthz;
import com.miragemock.admin.security.ProjectAuthzInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册项目级鉴权拦截器：对所有 /api/v1/projects/{pid}/** 路由校验成员权限。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final ProjectAuthz authz;

    public WebMvcConfig(ProjectAuthz authz) {
        this.authz = authz;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ProjectAuthzInterceptor(authz))
                .addPathPatterns("/api/v1/projects/**");
    }
}
