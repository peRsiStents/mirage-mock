-- 场景步骤增强：执行条件（引用上一步结果）+ 失败自动重试
ALTER TABLE test_scenario_step ADD COLUMN condition VARCHAR(128);
ALTER TABLE test_scenario_step ADD COLUMN retry_count INT DEFAULT 0;
ALTER TABLE test_scenario_step ADD COLUMN retry_delay_ms INT DEFAULT 1000;
