-- 蜃楼 Mock 初始化建表脚本（MySQL 语法，H2 MODE=MySQL / OceanBase MySQL 模式兼容）
-- 不使用 AUTO_INCREMENT/ENGINE/CHARSET，主键由应用层（雪花 ID）生成。

CREATE TABLE user_account (
    id            BIGINT       NOT NULL PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(128),
    nickname      VARCHAR(64),
    is_admin      TINYINT      DEFAULT 0,
    status        TINYINT      DEFAULT 1,
    create_time   DATETIME,
    update_time   DATETIME,
    CONSTRAINT uk_user_username UNIQUE (username)
);

CREATE TABLE project (
    id           BIGINT       NOT NULL PRIMARY KEY,
    name         VARCHAR(128),
    code         VARCHAR(64)  NOT NULL,
    remark       VARCHAR(512),
    rule_version INT          DEFAULT 1,
    status       TINYINT      DEFAULT 1,
    create_time  DATETIME,
    update_time  DATETIME,
    CONSTRAINT uk_project_code UNIQUE (code)
);

CREATE TABLE project_member (
    id          BIGINT      NOT NULL PRIMARY KEY,
    project_id  BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    member_role VARCHAR(16),
    create_time DATETIME
);

CREATE TABLE api_interface (
    id              BIGINT       NOT NULL PRIMARY KEY,
    project_id      BIGINT       NOT NULL,
    name            VARCHAR(128),
    protocol        VARCHAR(16)  DEFAULT 'HTTP',
    http_method     VARCHAR(8),
    http_path       VARCHAR(256),
    tcp_listener_id BIGINT,
    tcp_route_expr  VARCHAR(256),
    status          TINYINT      DEFAULT 1,
    remark          VARCHAR(512),
    create_time     DATETIME,
    update_time     DATETIME
);

CREATE TABLE mock_rule (
    id                BIGINT       NOT NULL PRIMARY KEY,
    interface_id      BIGINT       NOT NULL,
    name              VARCHAR(128),
    priority          INT          DEFAULT 100,
    match_condition   TEXT,
    response_template TEXT,
    delay_type        VARCHAR(16)  DEFAULT 'NONE',
    delay_ms          INT,
    delay_min_ms      INT,
    delay_max_ms      INT,
    fault_type        VARCHAR(16)  DEFAULT 'NONE',
    fault_config      TEXT,
    status            TINYINT      DEFAULT 1,
    create_time       DATETIME,
    update_time       DATETIME
);

CREATE TABLE secret_key (
    id          BIGINT      NOT NULL PRIMARY KEY,
    project_id  BIGINT      NOT NULL,
    alias       VARCHAR(64) NOT NULL,
    algorithm   VARCHAR(16),
    public_key  TEXT,
    private_key TEXT,
    iv_value    VARCHAR(128),
    create_time DATETIME,
    update_time DATETIME
);

CREATE TABLE mock_request_log (
    id             BIGINT      NOT NULL PRIMARY KEY,
    project_id     BIGINT,
    interface_id   BIGINT,
    rule_id        BIGINT,
    protocol       VARCHAR(16),
    client_addr    VARCHAR(64),
    request_raw    TEXT,
    request_parsed TEXT,
    response_raw   TEXT,
    matched        TINYINT,
    cost_ms        INT,
    create_time    DATETIME
);

CREATE TABLE mock_sequence (
    id            BIGINT      NOT NULL PRIMARY KEY,
    project_id    BIGINT      NOT NULL,
    seq_name      VARCHAR(64) NOT NULL,
    current_value BIGINT,
    create_time   DATETIME,
    update_time   DATETIME,
    CONSTRAINT uk_sequence UNIQUE (project_id, seq_name)
);

CREATE INDEX idx_log_project_time ON mock_request_log (project_id, create_time);
CREATE INDEX idx_interface_project ON api_interface (project_id);
CREATE INDEX idx_rule_interface ON mock_rule (interface_id);
CREATE INDEX idx_secret_project_alias ON secret_key (project_id, alias);
