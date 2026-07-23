-- M2: TCP 监听器表（MySQL 语法，H2 MODE=MySQL / OceanBase MySQL 模式兼容）

CREATE TABLE tcp_listener (
    id                    BIGINT       NOT NULL PRIMARY KEY,
    project_id            BIGINT       NOT NULL,
    name                  VARCHAR(128),
    port                  INT          NOT NULL,
    conn_mode             VARCHAR(16)  DEFAULT 'LONG',
    frame_config          TEXT,
    message_format        VARCHAR(32)  DEFAULT 'json',
    message_format_config TEXT,
    route_extract         VARCHAR(256),
    serial_extract        VARCHAR(256),
    match_mode            VARCHAR(16)  DEFAULT 'SYNC',
    push_config           TEXT,
    status                TINYINT      DEFAULT 1,
    create_time           DATETIME,
    update_time           DATETIME
);

CREATE INDEX idx_listener_project ON tcp_listener (project_id);
CREATE INDEX idx_listener_port ON tcp_listener (port);
