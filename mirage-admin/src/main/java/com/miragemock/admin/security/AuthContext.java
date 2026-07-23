package com.miragemock.admin.security;

/**
 * 当前登录用户上下文（ThreadLocal），由 JwtAuthFilter 注入。
 */
public final class AuthContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static Long currentUserId() {
        LoginUser u = HOLDER.get();
        return u == null ? null : u.getUserId();
    }

    public static boolean isAdmin() {
        LoginUser u = HOLDER.get();
        return u != null && u.isAdmin();
    }

    public static final class LoginUser {
        private final Long userId;
        private final String username;
        private final boolean admin;

        public LoginUser(Long userId, String username, boolean admin) {
            this.userId = userId;
            this.username = username;
            this.admin = admin;
        }

        public Long getUserId() {
            return userId;
        }

        public String getUsername() {
            return username;
        }

        public boolean isAdmin() {
            return admin;
        }
    }
}
