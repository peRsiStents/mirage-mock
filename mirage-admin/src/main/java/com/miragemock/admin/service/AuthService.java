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

    /**
     * 用户名不存在的账号也走一次 BCrypt 比对（固定哈希），
     * 使「用户不存在」与「密码错误」的响应耗时接近，避免通过耗时差异枚举用户名。
     *
     * <p>该哈希在构造时由 encoder 生成（保证是本编码器认可的有效格式，
     * 硬编码字符串可能因算法前缀/成本因子不匹配而被判为非法哈希）。
     */
    private final String dummyHash;

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
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing-equalization");
    }

    /**
     * 登录。
     *
     * @param clientIp 客户端 IP（可为 null；控制器已解析 X-Forwarded-For，作为限流维度）
     */
    public LoginResponse login(String username, String password, String clientIp) {
        String key = rateKey(username, clientIp);
        rateLimiter.check(key);
        UserAccount user = userMapper.selectOne(
                new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getUsername, username));
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            // 走一次假比对均衡「用户不存在」与「密码错误」的响应耗时
            passwordEncoder.matches(password == null ? "" : password, dummyHash);
            rateLimiter.onFailure(key);
            throw new BizException(ResultCode.LOGIN_FAILED);
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            rateLimiter.onFailure(key);
            throw new BizException(ResultCode.LOGIN_FAILED);
        }
        rateLimiter.onSuccess(key);
        boolean admin = user.getIsAdmin() != null && user.getIsAdmin() == 1;
        String token = jwtUtil.generate(user.getId(), user.getUsername(), admin);
        return new LoginResponse(token, user.getId(), user.getUsername(), admin);
    }

    /** 兼容旧调用（无显式 IP 维度）：从请求上下文取客户端 IP。 */
    public LoginResponse login(String username, String password) {
        return login(username, password, currentClientIp());
    }

    /** 限流维度：username@clientIp（透传 X-Forwarded-For 首段，便于网关部署） */
    private String rateKey(String username, String clientIp) {
        String ip = (clientIp == null || clientIp.isEmpty()) ? currentClientIp() : clientIp.trim();
        return (username == null ? "?" : username) + "@" + ip;
    }

    /** 从请求上下文解析客户端 IP：优先 X-Forwarded-For 最左项，回退直连地址 */
    private String currentClientIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                String xff = req.getHeader("X-Forwarded-For");
                if (xff != null && !xff.isEmpty()) {
                    return xff.split(",")[0].trim();
                }
                return req.getRemoteAddr();
            }
        } catch (Exception ignore) {
            // 取不到 IP 时退化为仅按用户名限流
        }
        return "unknown";
    }
}
