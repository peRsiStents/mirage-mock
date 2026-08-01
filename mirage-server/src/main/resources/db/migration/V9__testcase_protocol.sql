-- 测试用例支持 TCP 协议：HTTP(默认) / TCP
ALTER TABLE test_case ADD COLUMN protocol VARCHAR(8) DEFAULT 'HTTP';
-- TCP 专用配置：JSON {frameConfig, messageFormat, formatConfig}
ALTER TABLE test_case ADD COLUMN tcp_config TEXT;
