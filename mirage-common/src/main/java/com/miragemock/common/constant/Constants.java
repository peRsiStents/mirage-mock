package com.miragemock.common.constant;

/**
 * 系统常量
 */
public final class Constants {

    private Constants() {
    }

    /** 启用 */
    public static final int STATUS_ENABLED = 1;
    /** 停用 */
    public static final int STATUS_DISABLED = 0;

    /** 默认 Mock HTTP 端口 */
    public static final int DEFAULT_MOCK_HTTP_PORT = 19080;

    /** 默认管理端端口 */
    public static final int DEFAULT_ADMIN_PORT = 9080;

    /** API 统一前缀 */
    public static final String API_PREFIX = "/api/v1";

    /** 请求头：项目编码（Mock 请求可携带以定位项目，多项目共用端口时） */
    public static final String HEADER_PROJECT_CODE = "X-Mirage-Project";

    /** 默认规则优先级 */
    public static final int DEFAULT_PRIORITY = 100;

    /** 规则版本初始值 */
    public static final int INITIAL_RULE_VERSION = 1;
}
