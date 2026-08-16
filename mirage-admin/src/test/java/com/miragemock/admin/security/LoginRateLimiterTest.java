package com.miragemock.admin.security;

import com.miragemock.common.api.ResultCode;
import com.miragemock.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 登录限流器单测：频率限制、连续失败锁定、成功后解锁。
 */
class LoginRateLimiterTest {

    private final LoginRateLimiter limiter = new LoginRateLimiter();

    @Test
    void allowsNormalAttempts() {
        String key = "admin@127.0.0.1";
        for (int i = 0; i < 10; i++) {
            limiter.check(key);
        }
        // 第 11 次触发频率限制
        assertThrows(BizException.class, () -> limiter.check(key));
    }

    @Test
    void frequencyWindowSlides() throws InterruptedException {
        String key = "user@127.0.0.1";
        for (int i = 0; i < 10; i++) {
            limiter.check(key);
        }
        BizException e = assertThrows(BizException.class, () -> limiter.check(key));
        assertEquals(ResultCode.LOGIN_RATE_LIMITED, e.getResultCode());
    }

    @Test
    void consecutiveFailuresLock() {
        String key = "lock@127.0.0.1";
        for (int i = 0; i < 5; i++) {
            limiter.onFailure(key);
        }
        BizException e = assertThrows(BizException.class, () -> limiter.check(key));
        assertEquals(ResultCode.LOGIN_RATE_LIMITED, e.getResultCode());
    }

    @Test
    void successResetsLock() {
        String key = "reset@127.0.0.1";
        for (int i = 0; i < 5; i++) {
            limiter.onFailure(key);
        }
        assertThrows(BizException.class, () -> limiter.check(key));
        // 登录成功后解锁
        limiter.onSuccess(key);
        assertDoesNotThrow(() -> limiter.check(key));
    }

    @Test
    void failuresBelowThresholdStillAllowed() {
        String key = "warn@127.0.0.1";
        for (int i = 0; i < 4; i++) {
            limiter.onFailure(key);
        }
        assertDoesNotThrow(() -> limiter.check(key));
    }

    @Test
    void nullKeyIgnored() {
        assertDoesNotThrow(() -> limiter.check(null));
        assertDoesNotThrow(() -> limiter.onFailure(null));
        assertDoesNotThrow(() -> limiter.onSuccess(null));
    }
}
