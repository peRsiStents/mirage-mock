package com.miragemock.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.miragemock.admin.dto.LoginResponse;
import com.miragemock.admin.mapper.UserAccountMapper;
import com.miragemock.admin.security.JwtUtil;
import com.miragemock.common.api.ResultCode;
import com.miragemock.common.entity.UserAccount;
import com.miragemock.common.exception.BizException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    /** 单用户名连续失败上限，超过即锁定一段时间（防字典爆破；多副本部署需换 Redis 计数） */
    private static final int MAX_FAIL = 5;
    private static final long LOCK_MS = 5 * 60 * 1000L;
    private final Map<String, long[]> attempts = new ConcurrentHashMap<>(); // username -> [失败次数, 最近失败ms]

    private final UserAccountMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Autowired
    public AuthService(UserAccountMapper userMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public LoginResponse login(String username, String password) {
        String key = (username == null ? "" : username).trim().toLowerCase();
        long now = System.currentTimeMillis();
        long[] a = attempts.computeIfAbsent(key, k -> new long[]{0, 0});
        synchronized (a) {
            if (a[0] >= MAX_FAIL && now - a[1] < LOCK_MS) {
                long mins = ((LOCK_MS - (now - a[1])) + 59_999L) / 60_000L;
                throw new BizException(ResultCode.FORBIDDEN, "登录失败次数过多，请 " + Math.max(1, mins) + " 分钟后再试");
            }
            if (a[0] > 0 && now - a[1] > LOCK_MS) {
                a[0] = 0; // 超过锁定窗口，重置计数
            }
        }
        UserAccount user = userMapper.selectOne(
                new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getUsername, username));
        boolean ok = user != null && user.getStatus() != null && user.getStatus() == 1
                && passwordEncoder.matches(password, user.getPasswordHash());
        if (!ok) {
            synchronized (a) {
                a[0] = a[0] + 1;
                a[1] = now;
            }
            throw new BizException(ResultCode.LOGIN_FAILED);
        }
        synchronized (a) {
            a[0] = 0;
            a[1] = 0;
        }
        boolean admin = user.getIsAdmin() != null && user.getIsAdmin() == 1;
        String token = jwtUtil.generate(user.getId(), user.getUsername(), admin);
        return new LoginResponse(token, user.getId(), user.getUsername(), admin);
    }
}
