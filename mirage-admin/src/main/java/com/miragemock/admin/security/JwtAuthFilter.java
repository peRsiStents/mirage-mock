package com.miragemock.admin.security;

import com.miragemock.common.api.Result;
import com.miragemock.common.api.ResultCode;
import com.miragemock.common.util.JsonUtils;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * JWT 鉴权过滤器：保护 /api/v1/**（登录接口放行）。
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final String BEARER = "Bearer ";

    private final JwtUtil jwtUtil;

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/api/v1") || isPublic(path)) {
            chain.doFilter(request, response);
            return;
        }
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith(BEARER)) {
            unauthorized(response, ResultCode.UNAUTHORIZED);
            return;
        }
        String token = auth.substring(BEARER.length()).trim();
        try {
            Claims claims = jwtUtil.parse(token);
            Long uid = claims.get("uid", Long.class);
            if (uid == null) {
                uid = parseLong(claims.getId());
            }
            String username = claims.get("username", String.class);
            Boolean admin = claims.get("admin", Boolean.class);
            AuthContext.set(new AuthContext.LoginUser(uid, username, Boolean.TRUE.equals(admin)));
            chain.doFilter(request, response);
        } catch (Exception e) {
            log.debug("JWT 校验失败: {}", e.getMessage());
            unauthorized(response, ResultCode.UNAUTHORIZED);
        } finally {
            AuthContext.clear();
        }
    }

    private boolean isPublic(String path) {
        return path.equals("/api/v1/auth/login");
    }

    private Long parseLong(String s) {
        try {
            return s == null ? null : Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void unauthorized(HttpServletResponse response, ResultCode rc) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(JsonUtils.toJson(Result.fail(rc)));
        response.getWriter().flush();
    }
}
