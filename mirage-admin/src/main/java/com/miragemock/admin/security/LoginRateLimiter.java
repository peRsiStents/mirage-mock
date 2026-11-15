package com.miragemock.admin.security;

import com.miragemock.common.api.ResultCode;
import com.miragemock.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录限流器（进程内内存实现，单实例部署足够）。
 *
 * <p>按 {@code username@clientIp} 维度做两级防护：</p>
 * <ul>
 *   <li><b>频率限制</b>：每分钟最多 {@link #MAX_ATTEMPTS_PER_MINUTE} 次登录尝试，防止脚本爆破；</li>
 *   <li><b>连续失败锁定</b>：窗口内连续失败 {@link #MAX_FAILURES} 次后锁定 {@link #LOCK_MS}，锁定期间拒绝登录。</li>
 * </ul>
 */
@Component
public class LoginRateLimiter {

    private static final long WINDOW_MS = 60_000L;
    private static final long LOCK_MS = 10 * 60_000L;
    private static final int MAX_ATTEMPTS_PER_MINUTE = 10;
    private static final int MAX_FAILURES = 5;

    /** key → 窗口内各次尝试时间戳 */
    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();
    /** key → 锁定截止时间戳 */
    private final Map<String, Long> lockedUntil = new ConcurrentHashMap<>();

    /**
     * 登录前调用：若当前处于锁定或被限频，抛出 {@link BizException}(LOGIN_RATE_LIMITED)。
     *
     * @param key 通常是 username + clientIp 组合
     */
    public void check(String key) {
        if (key == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long lockUntil = lockedUntil.get(key);
        if (lockUntil != null) {
            if (now < lockUntil) {
                throw new BizException(ResultCode.LOGIN_RATE_LIMITED);
            }
            // 锁定期已过，清理
            lockedUntil.remove(key, lockUntil);
        }
        Deque<Long> deque = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst() < now - WINDOW_MS) {
                deque.pollFirst();
            }
            if (deque.size() >= MAX_ATTEMPTS_PER_MINUTE) {
                throw new BizException(ResultCode.LOGIN_RATE_LIMITED);
            }
            deque.addLast(now);
        }
    }

    /**
     * 登录成功后调用：清空该 key 的尝试记录、失败记录与锁定状态。
     */
    public void onSuccess(String key) {
        if (key == null) {
            return;
        }
        lockedUntil.remove(key);
        attempts.remove(key);
        attempts.remove("fail:" + key);
    }

    /**
     * 登录失败后调用：记录失败；连续失败达到阈值则锁定。
     */
    public void onFailure(String key) {
        if (key == null) {
            return;
        }
        // 失败次数单独计数（不依赖 attempts 的时间戳队列，避免频率窗口滑动重置失败计数）
        String failKey = "fail:" + key;
        long now = System.currentTimeMillis();
        Deque<Long> deque = attempts.computeIfAbsent(failKey, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst() < now - WINDOW_MS) {
                deque.pollFirst();
            }
            deque.addLast(now);
            if (deque.size() >= MAX_FAILURES) {
                lockedUntil.put(key, now + LOCK_MS);
                attempts.remove(failKey);
            }
        }
    }
}
