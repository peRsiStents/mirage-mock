package com.miragemock.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.miragemock.admin.dto.LoginResponse;
import com.miragemock.admin.mapper.UserAccountMapper;
import com.miragemock.admin.security.JwtUtil;
import com.miragemock.admin.security.LoginRateLimiter;
import com.miragemock.common.api.ResultCode;
import com.miragemock.common.entity.UserAccount;
import com.miragemock.common.exception.BizException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

@Service
public class AuthService {

    private final UserAccountMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final LoginRateLimiter rateLimiter;

    @Autowired
    public AuthService(UserAccountMapper userMapper, PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil, LoginRateLimiter rateLimiter) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.rateLimiter = rateLimiter;
    }

    public LoginResponse login(String username, String password) {
        String rateKey = rateKey(username);
        rateLimiter.check(rateKey);
        UserAccount user = userMapper.selectOne(
                new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getUsername, username));
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            rateLimiter.onFailure(rateKey);
            throw new BizException(ResultCode.LOGIN_FAILED);
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            rateLimiter.onFailure(rateKey);
            throw new BizException(ResultCode.LOGIN_FAILED);
        }
        rateLimiter.onSuccess(rateKey);
        boolean admin = user.getIsAdmin() != null && user.getIsAdmin() == 1;
        String token = jwtUtil.generate(user.getId(), user.getUsername(), admin);
        return new LoginResponse(token, user.getId(), user.getUsername(), admin);
    }

    /** 限流维度：username@clientIp（透传 X-Forwarded-For 首段，便于网关部署） */
    private String rateKey(String username) {
        String ip = "unknown";
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                String xff = req.getHeader("X-Forwarded-For");
                if (xff != null && !xff.isEmpty()) {
                    ip = xff.split(",")[0].trim();
                } else {
                    ip = req.getRemoteAddr();
                }
            }
        } catch (Exception ignore) {
            // 取不到 IP 时退化为仅按用户名限流
        }
        return (username == null ? "?" : username) + "@" + ip;
    }
}
