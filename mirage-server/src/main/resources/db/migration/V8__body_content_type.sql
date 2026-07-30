-- Postman 风格请求体：raw / binary 的显式 Content-Type（form-data / x-www 由转换器固定）
ALTER TABLE test_case ADD COLUMN body_content_type VARCHAR(128);
-- body_type 需容纳 x-www-form-urlencoded(21) 等新类型，原 VARCHAR(16) 不够
ALTER TABLE test_case ALTER COLUMN body_type SET DATA TYPE VARCHAR(32);
