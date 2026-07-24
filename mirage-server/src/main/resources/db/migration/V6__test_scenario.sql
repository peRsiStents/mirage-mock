-- Stage1: 测试场景编排 + 环境 + 运行报告

CREATE TABLE test_scenario (
    id          BIGINT       NOT NULL PRIMARY KEY,
    project_id  BIGINT       NOT NULL,
    name        VARCHAR(128),
    remark      VARCHAR(512),
    on_fail     VARCHAR(16)  DEFAULT 'STOP',
    env_id      BIGINT,
    status      TINYINT      DEFAULT 1,
    create_time DATETIME,
    update_time DATETIME
);
CREATE INDEX idx_scenario_project ON test_scenario (project_id);

CREATE TABLE test_scenario_step (
    id               BIGINT   NOT NULL PRIMARY KEY,
    scenario_id      BIGINT   NOT NULL,
    seq              INT      NOT NULL,
    case_id          BIGINT   NOT NULL,
    name             VARCHAR(128),
    extract          TEXT,
    continue_on_fail TINYINT  DEFAULT 0,
    enabled          TINYINT  DEFAULT 1,
    create_time      DATETIME,
    update_time      DATETIME
);
CREATE INDEX idx_step_scenario ON test_scenario_step (scenario_id);

CREATE TABLE test_environment (
    id          BIGINT       NOT NULL PRIMARY KEY,
    project_id  BIGINT       NOT NULL,
    name        VARCHAR(64),
    base_url    VARCHAR(256),
    variables   TEXT,
    status      TINYINT      DEFAULT 1,
    create_time DATETIME,
    update_time DATETIME
);
CREATE INDEX idx_env_project ON test_environment (project_id);

CREATE TABLE test_run_record (
    id            BIGINT   NOT NULL PRIMARY KEY,
    project_id    BIGINT   NOT NULL,
    target_type   VARCHAR(16),
    target_id     BIGINT,
    env_id        BIGINT,
    passed        TINYINT,
    total_steps   INT,
    passed_steps  INT,
    failed_steps  INT,
    cost_ms       BIGINT,
    detail        TEXT,
    create_time   DATETIME,
    update_time   DATETIME
);
CREATE INDEX idx_record_project ON test_run_record (project_id, create_time);
