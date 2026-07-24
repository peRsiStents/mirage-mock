-- P2: 测试变量/常量（项目级，测试用例以 var.变量名 引用）

CREATE TABLE test_variable (
    id          BIGINT       NOT NULL PRIMARY KEY,
    project_id  BIGINT       NOT NULL,
    name        VARCHAR(64)  NOT NULL,
    var_value   VARCHAR(1024),
    remark      VARCHAR(256),
    status      TINYINT      DEFAULT 1,
    create_time DATETIME,
    update_time DATETIME
);
CREATE INDEX idx_testvar_project ON test_variable (project_id);
