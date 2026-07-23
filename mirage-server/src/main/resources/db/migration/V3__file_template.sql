-- M3(P1): 批量文件生成模板（MySQL 语法，H2 MODE=MySQL / OceanBase MySQL 模式兼容）

CREATE TABLE file_template (
    id             BIGINT       NOT NULL PRIMARY KEY,
    project_id     BIGINT       NOT NULL,
    name           VARCHAR(128),
    header_line    VARCHAR(512),
    row_template   TEXT,
    row_count      INT          DEFAULT 100,
    encoding       VARCHAR(16)  DEFAULT 'GBK',
    line_separator VARCHAR(8)   DEFAULT 'CRLF',
    file_ext       VARCHAR(8)   DEFAULT 'txt',
    status         TINYINT      DEFAULT 1,
    remark         VARCHAR(512),
    create_time    DATETIME,
    update_time    DATETIME
);

CREATE INDEX idx_filetpl_project ON file_template (project_id);
