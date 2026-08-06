-- test_run_log 仅有 (case_id, create_time) 索引；工作台「运行通过率 / 最近运行」按 project_id 聚合，
-- 多用户/多项目下缺索引会全表扫。补 (project_id, create_time) 复合索引。
CREATE INDEX idx_runlog_project ON test_run_log (project_id, create_time);
