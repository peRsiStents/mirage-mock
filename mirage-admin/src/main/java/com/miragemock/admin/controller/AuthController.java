package com.miragemock.admin.controller;

import com.miragemock.admin.dto.LoginRequest;
import com.miragemock.admin.dto.LoginResponse;
import com.miragemock.admin.service.AuthService;
import com.miragemock.common.api.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    @Autowired
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                       HttpServletRequest httpRequest) {
        return Result.ok(authService.login(request.getUsername(), request.getPassword(), clientIp(httpRequest)));
    }

    /**
     * 取客户端 IP：优先代理声明的头（X-Forwarded-For 最左项），回退直连地址。
     *
     * <p>仅用于登录失败计数的 IP 维度限流，不作安全边界使用——
     * 头可被伪造，因此攻击者可以绕过该维度，但账号维度的锁定依然生效。
     */
    private String clientIp(HttpServletRequest request) {
        String fwd = request.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.trim().isEmpty()) {
            int comma = fwd.indexOf(',');
            String first = (comma > 0 ? fwd.substring(0, comma) : fwd).trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.trim().isEmpty()) {
            return real.trim();
        }
        return request.getRemoteAddr();
    }
}
