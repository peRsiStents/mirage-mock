-- Stage2: 定时任务表 + 数据驱动列 + CI token 列（Flyway 增量；H2/MySQL/OceanBase 通用）

CREATE TABLE test_schedule (
    id            BIGINT       NOT NULL PRIMARY KEY,
    project_id    BIGINT       NOT NULL,
    scenario_id   BIGINT       NOT NULL,
    name          VARCHAR(128),
    cron          VARCHAR(64)  NOT NULL,
    env_id        BIGINT,
    enabled       TINYINT      DEFAULT 1,
    last_run_time DATETIME,
    last_passed   TINYINT,
    last_cost_ms  BIGINT,
    remark        VARCHAR(256),
    create_time   DATETIME,
    update_time   DATETIME
);
CREATE INDEX idx_schedule_project ON test_schedule (project_id);
CREATE INDEX idx_schedule_scenario ON test_schedule (scenario_id);

ALTER TABLE test_case ADD COLUMN data_set TEXT;
ALTER TABLE project ADD COLUMN ci_token VARCHAR(64);
