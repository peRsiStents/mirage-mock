package com.miragemock.admin.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 启动安全校验单测：prod/test profile 下默认密钥必须 fail-fast；显式注入后放行；local 不拦截。
 */
class SecurityStartupValidatorTest {

    private SecurityProperties props() {
        SecurityProperties p = new SecurityProperties();
        p.setJwtSecret("mirage-mock-dev-jwt-secret-please-change-in-prod-0123456789");
        p.setMasterKey("mirage-mock-dev-master-key");
        return p;
    }

    @Test
    void prodWithDefaultSecrets_failsFast() {
        SecurityStartupValidator v = new SecurityStartupValidator(props(), "prod");
        assertThrows(IllegalStateException.class, v::validate);
    }

    @Test
    void prodWithJwtOverridden_stillFailsOnMasterKey() {
        SecurityProperties p = props();
        p.setJwtSecret("a-strong-production-jwt-secret-0123456789abcdef");
        SecurityStartupValidator v = new SecurityStartupValidator(p, "prod");
        assertThrows(IllegalStateException.class, v::validate);
    }

    @Test
    void prodWithAllSecretsInjected_passes() {
        SecurityProperties p = props();
        p.setJwtSecret("a-strong-production-jwt-secret-0123456789abcdef");
        p.setMasterKey("a-strong-production-master-key");
        SecurityStartupValidator v = new SecurityStartupValidator(p, "prod");
        assertDoesNotThrow(v::validate);
    }

    @Test
    void testProfile_alsoChecked() {
        SecurityStartupValidator v = new SecurityStartupValidator(props(), "test");
        assertThrows(IllegalStateException.class, v::validate);
    }

    @Test
    void localProfile_notChecked() {
        SecurityStartupValidator v = new SecurityStartupValidator(props(), "local");
        assertDoesNotThrow(v::validate);
    }

    @Test
    void multipleProfiles_containsProd() {
        SecurityStartupValidator v = new SecurityStartupValidator(props(), "local,prod");
        assertThrows(IllegalStateException.class, v::validate);
    }
}
