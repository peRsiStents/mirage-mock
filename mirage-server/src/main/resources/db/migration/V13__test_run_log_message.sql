-- 测试用例「记录查看」：把每次运行实际发出的请求报文与收到的返回报文落到运行记录里。
--
-- 背景：test_run_log 之前只记 mode/status/cost/passed/assertion/error，
-- 事后想知道「这条用例当时到底发了什么、回了什么」只能重跑一次；
-- 而重跑可能因数据/环境变化得到不同结果，无法复现当时的现场。
--
-- 新增三列（均为 TEXT，不设默认值，兼容 MySQL 8 与 H2 MySQL 模式）：
--   request_info     请求报文 JSON：HTTP {method,url,headers,body} / TCP {target,frame,body,raw}
--   response_body    返回报文体
--   response_headers 返回报文头 JSON
--
-- 存量行这三列为 NULL，UI 显示「（该记录未保存报文）」。

ALTER TABLE test_run_log ADD COLUMN request_info TEXT;
ALTER TABLE test_run_log ADD COLUMN response_body TEXT;
ALTER TABLE test_run_log ADD COLUMN response_headers TEXT;
