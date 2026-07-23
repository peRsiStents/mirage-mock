-- P2: 测试案例管理（MySQL 语法，H2 MODE=MySQL / OceanBase MySQL 模式兼容）

CREATE TABLE test_case (
    id         BIGINT       NOT NULL PRIMARY KEY,
    project_id BIGINT       NOT NULL,
    name       VARCHAR(128),
    method     VARCHAR(16)  DEFAULT 'GET',
    url        VARCHAR(512),
    headers    TEXT,
    query      TEXT,
    body_type  VARCHAR(16)  DEFAULT 'none',
    body       TEXT,
    assertions TEXT,
    mode       VARCHAR(16)  DEFAULT 'proxy',
    status     TINYINT      DEFAULT 1,
    remark     VARCHAR(512),
    create_time DATETIME,
    update_time DATETIME
);
CREATE INDEX idx_testcase_project ON test_case (project_id);

CREATE TABLE test_run_log (
    id               BIGINT   NOT NULL PRIMARY KEY,
    project_id       BIGINT   NOT NULL,
    case_id          BIGINT   NOT NULL,
    mode             VARCHAR(16),
    http_status      INT,
    cost_ms          BIGINT,
    passed           TINYINT,
    assertion_result TEXT,
    error            TEXT,
    create_time      DATETIME,
    update_time      DATETIME
);
CREATE INDEX idx_runlog_case ON test_run_log (case_id, create_time);
