-- 测试用例标签（分组/筛选）：JSON 数组字符串，如 ["smoke","slow"]
ALTER TABLE test_case ADD COLUMN tags VARCHAR(512);
