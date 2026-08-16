package com.miragemock.admin.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * 启动安全校验（fail-fast）：
 * 生产/测试 profile 下，若 JWT 签名密钥或密钥落库主密钥仍是内置默认值，直接拒绝启动。
 *
 * <p>背景：默认密钥写死在代码中，一旦生产环境忘记通过 {@code MIRAGE_JWT_SECRET} /
 * {@code MIRAGE_MASTER_KEY} 覆盖，JWT 可被伪造、私钥落库加密形同虚设。
 * 与其运行在"看似安全实则裸奔"的状态，不如启动即失败。</p>
 */
@Component
public class SecurityStartupValidator {

    private static final Logger log = LoggerFactory.getLogger(SecurityStartupValidator.class);

    private static final String DEFAULT_JWT_SECRET = "mirage-mock-dev-jwt-secret-please-change-in-prod-0123456789";
    private static final String DEFAULT_MASTER_KEY = "mirage-mock-dev-master-key";

    private final SecurityProperties props;
    private final String activeProfiles;

    @Autowired
    public SecurityStartupValidator(SecurityProperties props,
                                    @Value("${spring.profiles.active:}") String activeProfiles) {
        this.props = props;
        this.activeProfiles = activeProfiles == null ? "" : activeProfiles;
    }

    @PostConstruct
    public void validate() {
        if (!(containsProfile("prod") || containsProfile("test"))) {
            return;
        }
        if (DEFAULT_JWT_SECRET.equals(props.getJwtSecret())) {
            throw new IllegalStateException(
                    "[安全] 当前 profile 为 " + activeProfiles + "，但 MIRAGE_JWT_SECRET 仍是内置默认值。"
                            + "请通过环境变量 MIRAGE_JWT_SECRET 显式注入（≥32 字节），拒绝使用默认密钥启动。");
        }
        if (DEFAULT_MASTER_KEY.equals(props.getMasterKey())) {
            throw new IllegalStateException(
                    "[安全] 当前 profile 为 " + activeProfiles + "，但 MIRAGE_MASTER_KEY 仍是内置默认值。"
                            + "请通过环境变量 MIRAGE_MASTER_KEY 显式注入，拒绝使用默认主密钥启动。");
        }
        log.info("启动安全校验通过：JWT 密钥与落库主密钥均为显式注入");
    }

    private boolean containsProfile(String p) {
        for (String s : activeProfiles.split(",")) {
            if (p.equals(s.trim())) {
                return true;
            }
        }
        return false;
    }
}
