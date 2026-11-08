package com.miragemock.admin.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.miragemock.admin.mapper.UserAccountMapper;
import com.miragemock.common.entity.UserAccount;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 令牌撤销检查：账号被停用/删除后，已签发的 JWT 应尽快失效。
 *
 * <p>JWT 本身无状态，签发后在 TTL 内一直有效——管理员停用某账号时，
 * 该账号的旧 token 仍能继续访问（最长 12 小时）。
 *
 * <p>这里在每次鉴权时校验账号状态，并加一个短 TTL 缓存（默认 60s）：
 * 停用后最多 60s 生效，同时避免每个请求都查一次库。
 */
@Component
public class TokenRevocationChecker {

    /** 状态缓存 TTL（毫秒）：停用/删除后最多这么久生效 */
    private static final long CACHE_TTL_MS = 60_000L;

    /** 缓存条目上限：防止 userId 维度被无限灌入（内部生成，风险低，仍做上限保护） */
    private static final int MAX_CACHE_ENTRIES = 10_000;

    private final UserAccountMapper userMapper;
    private final Map<Long, Entry> cache = new ConcurrentHashMap<>();

    public TokenRevocationChecker(UserAccountMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 校验账号是否仍然可用。
     *
     * @return true=账号有效；false=账号不存在或已停用（token 应失效）
     */
    public boolean isActive(Long userId) {
        if (userId == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        Entry e = cache.get(userId);
        if (e != null && now - e.checkedAt < CACHE_TTL_MS) {
            return e.active;
        }
        boolean active = queryActive(userId);
        if (cache.size() > MAX_CACHE_ENTRIES) {
            cache.clear();
        }
        cache.put(userId, new Entry(active, now));
        return active;
    }

    /** 账号状态变更时调用，立即失效缓存（如修改密码/停用/改管理员标记）。 */
    public void invalidate(Long userId) {
        if (userId != null) {
            cache.remove(userId);
        }
    }

    private boolean queryActive(Long userId) {
        try {
            UserAccount u = userMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                    .select(UserAccount::getId, UserAccount::getStatus)
                    .eq(UserAccount::getId, userId));
            return u != null && u.getStatus() != null && u.getStatus() == 1;
        } catch (Exception e) {
            // 查询异常时保守放行（避免数据库抖动导致全员 401）；
            // 真正的账号停用会在下一次缓存过期后被检出。
            return true;
        }
    }

    private static final class Entry {
        final boolean active;
        final long checkedAt;

        Entry(boolean active, long checkedAt) {
            this.active = active;
            this.checkedAt = checkedAt;
        }
    }
}
