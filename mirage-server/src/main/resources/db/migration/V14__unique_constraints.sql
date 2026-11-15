-- 唯一约束补齐：把「仅靠应用层先查后插」的唯一性规则下沉到数据库。
--
-- 问题：项目成员、密钥别名、测试变量名等唯一性此前只在 Service 里做
--       selectCount → insert 的先查后插。两个并发请求可同时通过检查，
--       产生重复行（成员重复、密钥别名冲突覆盖、变量名歧义）。
--       数据库唯一约束是最后一道防线（并发下由 DB 拒绝，应用层捕获 DuplicateKeyException → 409）。
--
-- 迁移前先清理历史重复行（保留 id 最小的一条），避免加约束时因存量数据失败而无法启动。

-- 1) 项目成员：同一项目同一用户只能有一条
DELETE FROM project_member
WHERE id NOT IN (
    SELECT id FROM (
        SELECT MIN(id) AS id FROM project_member GROUP BY project_id, user_id
    ) x
);
ALTER TABLE project_member ADD CONSTRAINT uk_member_project_user UNIQUE (project_id, user_id);

-- 2) 密钥别名：同一项目同一别名只能有一个
DELETE FROM secret_key
WHERE id NOT IN (
    SELECT id FROM (
        SELECT MIN(id) AS id FROM secret_key GROUP BY project_id, alias
    ) x
);
ALTER TABLE secret_key ADD CONSTRAINT uk_secret_project_alias UNIQUE (project_id, alias);

-- 3) 测试变量名：同一项目同一变量名只能有一个
DELETE FROM test_variable
WHERE id NOT IN (
    SELECT id FROM (
        SELECT MIN(id) AS id FROM test_variable GROUP BY project_id, name
    ) x
);
ALTER TABLE test_variable ADD CONSTRAINT uk_testvar_project_name UNIQUE (project_id, name);

-- 4) 接口路由查询索引（非唯一）
--    说明：(http_method, http_path) 的唯一性由应用层全局校验（InterfaceService.ensureUniquePath）
--    维护，跨项目唯一。这里不加唯一约束：同名接口可能分属不同项目，合并/删除会跨项目搬运
--    规则、造成数据串项目；改为提供查询索引，让全局查重与路由扫描更快。
CREATE INDEX idx_interface_route ON api_interface (http_method, http_path);
