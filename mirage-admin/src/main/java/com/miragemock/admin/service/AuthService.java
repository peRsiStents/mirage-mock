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

    /** 单账号连续失败上限，超过即锁定一段时间（防字典爆破；多副本部署需换 Redis 计数） */
    private static final int MAX_FAIL = 5;
    /** 单 IP 的失败上限：防止用一个账号名对多个账号轮询爆破 */
    private static final int MAX_FAIL_PER_IP = 20;
    private static final long LOCK_MS = 5 * 60 * 1000L;

    /**
     * 失败计数表条目上限：超过则清空（防止攻击者用无限用户名/IP 撑爆内存）。
     */
    private static final int MAX_ATTEMPT_ENTRIES = 10_000;

    /**
     * 用户名不存在的账号也走一次 BCrypt 比对（固定哈希），
     * 使「用户不存在」与「密码错误」的响应耗时接近，避免通过耗时差异枚举用户名。
     *
     * <p>该哈希在构造时由 encoder 生成（保证是本编码器认可的有效格式，
     * 硬编码字符串可能因算法前缀/成本因子不匹配而被判为非法哈希）。
     */
    private final String dummyHash;

    private final Map<String, long[]> attempts = new ConcurrentHashMap<>(); // key -> [失败次数, 最近失败ms]
    private final Map<String, long[]> ipAttempts = new ConcurrentHashMap<>(); // ip -> [失败次数, 最近失败ms]

    private final UserAccountMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Autowired
    public AuthService(UserAccountMapper userMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing-equalization");
    }

    /**
     * 登录。
     *
     * @param clientIp 客户端 IP（可为 null；用于 IP 维度限流）
     */
    public LoginResponse login(String username, String password, String clientIp) {
        String key = (username == null ? "" : username).trim().toLowerCase();
        String ipKey = (clientIp == null || clientIp.isEmpty()) ? "" : clientIp.trim();
        long now = System.currentTimeMillis();

        long[] a = attempts.computeIfAbsent(key, k -> new long[]{0, 0});
        synchronized (a) {
            if (a[0] >= MAX_FAIL && now - a[1] < LOCK_MS) {
                throw locked(now - a[1]);
            }
            if (a[0] > 0 && now - a[1] > LOCK_MS) {
                a[0] = 0; // 超过锁定窗口，重置计数
            }
        }
        if (!ipKey.isEmpty()) {
            long[] ip = ipAttempts.computeIfAbsent(ipKey, k -> new long[]{0, 0});
            synchronized (ip) {
                if (ip[0] >= MAX_FAIL_PER_IP && now - ip[1] < LOCK_MS) {
                    throw locked(now - ip[1]);
                }
                if (ip[0] > 0 && now - ip[1] > LOCK_MS) {
                    ip[0] = 0;
                }
            }
        }

        UserAccount user = null;
        try {
            user = userMapper.selectOne(
                    new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getUsername, username));
        } catch (Exception ignored) {
            // 查询异常按「用户不存在」处理，不泄露内部错误
        }
        boolean ok;
        if (user == null) {
            // 假比对：消耗与真实校验相当的时间，避免用户名枚举
            passwordEncoder.matches(password == null ? "" : password, dummyHash);
            ok = false;
        } else {
            ok = user.getStatus() != null && user.getStatus() == 1
                    && passwordEncoder.matches(password == null ? "" : password, user.getPasswordHash());
        }
        if (!ok) {
            recordFailure(a, now);
            if (!ipKey.isEmpty()) {
                long[] ip = ipAttempts.computeIfAbsent(ipKey, k -> new long[]{0, 0});
                recordFailure(ip, now);
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

    /** 兼容旧调用（无 IP 维度）。 */
    public LoginResponse login(String username, String password) {
        return login(username, password, null);
    }

    private void recordFailure(long[] slot, long now) {
        synchronized (slot) {
            slot[0] = slot[0] + 1;
            slot[1] = now;
        }
        if (attempts.size() > MAX_ATTEMPT_ENTRIES) {
            attempts.clear();
        }
        if (ipAttempts.size() > MAX_ATTEMPT_ENTRIES) {
            ipAttempts.clear();
        }
    }

    private BizException locked(long elapsed) {
        long mins = ((LOCK_MS - elapsed) + 59_999L) / 60_000L;
        return new BizException(ResultCode.FORBIDDEN, "登录失败次数过多，请 " + Math.max(1, mins) + " 分钟后再试");
    }
}
