-- 录制回放（代理模式）：
--   1) api_interface 增加录制模式（record_mode: 0=关闭 1=录制 2=回放）与上游地址 upstream_url
--   2) recorded_response：录制响应快照，按 record_key（method|path|query|bodyHash 的 MD5）回放匹配
-- 兼容性：字段类型遵循 H2/MySQL/OceanBase 通用写法（不用 MEDIUMTEXT，统一 TEXT）

ALTER TABLE api_interface ADD COLUMN record_mode TINYINT DEFAULT 0;
ALTER TABLE api_interface ADD COLUMN upstream_url VARCHAR(512);

CREATE TABLE recorded_response (
    id            BIGINT       NOT NULL PRIMARY KEY,
    project_id    BIGINT       NOT NULL,
    interface_id  BIGINT,
    method        VARCHAR(8),
    path          VARCHAR(256),
    record_key    VARCHAR(64)  NOT NULL,
    status        INT          DEFAULT 200,
    headers       TEXT,
    body          TEXT,
    hit_count     INT          DEFAULT 0,
    last_hit_time DATETIME,
    create_time   DATETIME,
    update_time   DATETIME
);
CREATE INDEX idx_recorded_key ON recorded_response (project_id, record_key);
CREATE INDEX idx_recorded_iface ON recorded_response (interface_id);
