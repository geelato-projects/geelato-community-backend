-- ======================================================================
-- ide_script & ide_sync_log 建表 + Mock 数据
-- 用途：IDE/AI 协作的 GraalJS/Python/Wasm 脚本文件化存储 + 操作审计
-- 执行：直接在 MySQL（geelato 库）里跑即可；已存在表会先 DROP 再 CREATE
-- 日期：2026-07-24
-- ======================================================================

-- ----------------------------------------------------------------------
-- 1. 建表：ide_script
-- ----------------------------------------------------------------------
DROP TABLE IF EXISTS `ide_script`;
CREATE TABLE `ide_script` (
    `id`               VARCHAR(32)  NOT NULL COMMENT '主键',
    `code`             VARCHAR(128) NOT NULL COMMENT '业务编码（文件名主键，租户内唯一）',
    `name`             VARCHAR(128) NOT NULL COMMENT '显示名',
    `group_name`       VARCHAR(64)           DEFAULT NULL COMMENT '分组',
    `language`         VARCHAR(16)  NOT NULL DEFAULT 'js'  COMMENT '语言：js/python/wasm',
    `content`          MEDIUMTEXT           DEFAULT NULL COMMENT '脚本正文（JS/Python 源；wasm 留空）',
    `wasm_object_name` VARCHAR(256)         DEFAULT NULL COMMENT 'wasm 字节码在 OSS/本地磁盘的 objectName',
    `file_hash`        VARCHAR(64)          DEFAULT NULL COMMENT 'content 的 sha256（同步冲突检测用）',
    `version`          INT          NOT NULL DEFAULT 1    COMMENT '乐观锁版本',
    `status`           VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/ARCHIVED',
    `env_scope`        VARCHAR(64)  NOT NULL DEFAULT 'dev' COMMENT 'dev/staging/prod',
    `description`      VARCHAR(512)         DEFAULT NULL COMMENT '描述',
    `default_params`   TEXT                 DEFAULT NULL COMMENT '默认 dry-run 参数 JSON',
    `tenant_code`      VARCHAR(32)          DEFAULT NULL,
    `app_id`           VARCHAR(32)          DEFAULT NULL,
    `bu_id`            VARCHAR(32)          DEFAULT NULL,
    `dept_id`          VARCHAR(32)          DEFAULT NULL,
    `creator`          VARCHAR(32)          DEFAULT NULL,
    `creator_name`     VARCHAR(64)          DEFAULT NULL,
    `create_at`        DATETIME             DEFAULT NULL,
    `updater`          VARCHAR(32)          DEFAULT NULL,
    `updater_name`     VARCHAR(64)          DEFAULT NULL,
    `update_at`        DATETIME             DEFAULT NULL,
    `delete_at`        DATETIME             DEFAULT NULL,
    `del_status`       INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除：1=已删除，0=未删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code_tenant` (`code`, `tenant_code`),
    KEY `idx_group_name` (`group_name`),
    KEY `idx_status` (`status`),
    KEY `idx_language` (`language`),
    KEY `idx_creator` (`creator`),
    KEY `idx_tenant_status` (`tenant_code`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IDE 脚本（JS/Python/Wasm）';


-- ----------------------------------------------------------------------
-- 2. 建表：ide_sync_log
-- ----------------------------------------------------------------------
DROP TABLE IF EXISTS `ide_sync_log`;
CREATE TABLE `ide_sync_log` (
    `id`              VARCHAR(32)  NOT NULL COMMENT '主键',
    `script_id`       VARCHAR(32)  NOT NULL COMMENT '脚本主键',
    `script_code`     VARCHAR(128) NOT NULL COMMENT '脚本业务编码',
    `action`          VARCHAR(16)  NOT NULL COMMENT 'PULL/PUSH/DRYRUN/CREATE/UPDATE/DELETE/PUBLISH',
    `direction`       VARCHAR(16)           DEFAULT NULL COMMENT 'FILE_TO_DB/DB_TO_FILE',
    `before_hash`     VARCHAR(64)           DEFAULT NULL,
    `after_hash`      VARCHAR(64)           DEFAULT NULL,
    `before_version`  INT                   DEFAULT NULL,
    `after_version`   INT                   DEFAULT NULL,
    `operator`        VARCHAR(32)  NOT NULL COMMENT '操作人 userId',
    `operator_name`   VARCHAR(64)           DEFAULT NULL,
    `tenant_code`     VARCHAR(32)           DEFAULT NULL,
    `result`          VARCHAR(16)  NOT NULL COMMENT 'SUCCESS/FAIL/REJECTED',
    `message`         VARCHAR(512)          DEFAULT NULL,
    `duration_ms`     BIGINT                DEFAULT NULL,
    `client_ip`       VARCHAR(64)           DEFAULT NULL,
    `user_agent`      VARCHAR(256)          DEFAULT NULL,
    `create_at`       DATETIME     NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_script_id` (`script_id`),
    KEY `idx_script_code` (`script_code`),
    KEY `idx_action_result` (`action`, `result`),
    KEY `idx_operator` (`operator`),
    KEY `idx_tenant_create` (`tenant_code`, `create_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IDE 同步与操作审计';


-- ======================================================================
-- 3. Mock 数据：ide_script（8 条，覆盖 JS/Python、DRAFT/PUBLISHED、多分组）
--    所有 content 用合法的 geelato 脚本，可直接 dry-run
-- ======================================================================

-- 清理旧 mock（避免重复执行报唯一键冲突）
DELETE FROM `ide_script` WHERE `id` LIKE 'mock_ide_script_%';

-- ----- 3.1 JS 脚本（5 条）-----

-- ① 简单返回（最小验证用例）
INSERT INTO `ide_script` (`id`, `code`, `name`, `group_name`, `language`, `content`, `file_hash`, `version`, `status`, `env_scope`, `description`, `default_params`, `tenant_code`, `app_id`, `creator`, `creator_name`, `create_at`, `updater`, `updater_name`, `update_at`, `del_status`) VALUES
('mock_ide_script_01', 'hello_world', 'Hello World', 'demo', 'js',
'return 1 + 2;',
'a665a45920422f9d41794882bcb1f8e1f7e3d4a8c9b0e1f2a3b4c5d6e7f8a9b0', 1, 'PUBLISHED', 'dev',
'最小验证用例：返回 1+2 的结果', NULL,
'geelato', 'app_demo', 'admin', '管理员', NOW(), 'admin', '管理员', NOW(), 0);

-- ② $gl.dao 查询（最常见的业务脚本）
INSERT INTO `ide_script` (`id`, `code`, `name`, `group_name`, `language`, `content`, `file_hash`, `version`, `status`, `env_scope`, `description`, `default_params`, `tenant_code`, `app_id`, `creator`, `creator_name`, `create_at`, `updater`, `updater_name`, `update_at`, `del_status`) VALUES
('mock_ide_script_02', 'queryUserOrders', '查询用户订单', 'order', 'js',
'var rows = $gl.dao.queryForMapList("platform_user|@fs=id,login_name,user_name|del_status=0|@p=1,5");\nreturn rows;',
'b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2', 3, 'PUBLISHED', 'dev',
'按当前用户查询订单列表，分页前 5 条', NULL,
'geelato', 'app_demo', 'admin', '管理员', NOW(), 'admin', '管理员', NOW(), 0);

-- ③ $gl.http 调用 + $gl.json 处理
INSERT INTO `ide_script` (`id`, `code`, `name`, `group_name`, `language`, `content`, `file_hash`, `version`, `status`, `env_scope`, `description`, `default_params`, `tenant_code`, `app_id`, `creator`, `creator_name`, `create_at`, `updater`, `updater_name`, `update_at`, `del_status`) VALUES
('mock_ide_script_03', 'fetchWeather', '获取天气', 'integration', 'js',
'var url = "https://api.example.com/weather?city=" + (parameter.city || "shanghai");\nvar resp = $gl.http.get(url, null, {"Accept": "application/json"});\nvar data = $gl.json.toObject(resp);\nreturn {city: parameter.city, temp: data.main && data.main.temp};',
'c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4', 1, 'DRAFT', 'dev',
'调用外部天气 API 并返回简化结果', '{"city": "shanghai"}',
'geelato', 'app_demo', 'dev01', '开发者A', NOW(), 'dev01', '开发者A', NOW(), 0);

-- ④ $gl.dao 保存 + 事务（dry-run 会回滚，验证安全闭环）
INSERT INTO `ide_script` (`id`, `code`, `name`, `group_name`, `language`, `content`, `file_hash`, `version`, `status`, `env_scope`, `description`, `default_params`, `tenant_code`, `app_id`, `creator`, `creator_name`, `create_at`, `updater`, `updater_name`, `update_at`, `del_status`) VALUES
('mock_ide_script_04', 'createOrderDraft', '创建订单草稿', 'order', 'js',
'var result = $gl.dao.save("platform_example", "name=测试订单_" + Date.now() + "|description=dry-run创建");\nreturn result;',
'd5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6', 1, 'DRAFT', 'dev',
'演示 dry-run 写库回滚（save 不实际落库）', NULL,
'geelato', 'app_demo', 'dev01', '开发者A', NOW(), 'dev01', '开发者A', NOW(), 0);

-- ⑤ $gl.user + $gl.fn（取当前用户 + 工具函数）
INSERT INTO `ide_script` (`id`, `code`, `name`, `group_name`, `language`, `content`, `file_hash`, `version`, `status`, `env_scope`, `description`, `default_params`, `tenant_code`, `app_id`, `creator`, `creator_name`, `create_at`, `updater`, `updater_name`, `update_at`, `del_status`) VALUES
('mock_ide_script_05', 'currentUserInfo', '当前用户信息', 'system', 'js',
'var userId = $gl.user.userId;\nvar userName = $gl.user.userName;\nvar amount = 1234.56;\nreturn {\n  userId: userId,\n  userName: userName,\n  amountCN: $gl.fn.toChineseCurrency(amount),\n  now: $gl.fn.dateText("yyyy-MM-dd HH:mm:ss", null)\n};',
'e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8', 2, 'PUBLISHED', 'dev',
'返回当前登录用户信息 + 金额大写 + 当前时间', NULL,
'geelato', 'app_demo', 'admin', '管理员', NOW(), 'admin', '管理员', NOW(), 0);


-- ----- 3.2 Python 脚本（2 条）-----

-- ⑥ Python 基础（polyglot 拿 $gl）
INSERT INTO `ide_script` (`id`, `code`, `name`, `group_name`, `language`, `content`, `file_hash`, `version`, `status`, `env_scope`, `description`, `default_params`, `tenant_code`, `app_id`, `creator`, `creator_name`, `create_at`, `updater`, `updater_name`, `update_at`, `del_status`) VALUES
('mock_ide_script_06', 'pythonHello', 'Python 示例', 'demo', 'python',
'gl = polyglot.import_value("$gl")\nrows = gl.dao.queryForMapList("platform_dict|@fs=id,name|@p=1,3")\n__result__ = {"count": len(rows), "rows": rows}',
'f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0', 1, 'DRAFT', 'dev',
'Python 通过 polyglot 访问 $gl.dao 查询字典', NULL,
'geelato', 'app_demo', 'dev02', '开发者B', NOW(), 'dev02', '开发者B', NOW(), 0);

-- ⑦ Python 数据处理（列表推导）
INSERT INTO `ide_script` (`id`, `code`, `name`, `group_name`, `language`, `content`, `file_hash`, `version`, `status`, `env_scope`, `description`, `default_params`, `tenant_code`, `app_id`, `creator`, `creator_name`, `create_at`, `updater`, `updater_name`, `update_at`, `del_status`) VALUES
('mock_ide_script_07', 'pythonSumSquares', '平方和', 'demo', 'python',
'nums = range(1, 11)\nsquares = [n * n for n in nums]\ntotal = sum(squares)\n__result__ = {"nums": list(nums), "squares": squares, "total": total}',
'a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2', 1, 'DRAFT', 'dev',
'Python 计算 1-10 的平方和（演示纯计算能力）', NULL,
'geelato', 'app_demo', 'dev02', '开发者B', NOW(), 'dev02', '开发者B', NOW(), 0);


-- ----- 3.3 Wasm 脚本（1 条，content 和 wasm_object_name 均留空，仅演示元数据）-----
INSERT INTO `ide_script` (`id`, `code`, `name`, `group_name`, `language`, `content`, `wasm_object_name`, `file_hash`, `version`, `status`, `env_scope`, `description`, `default_params`, `tenant_code`, `app_id`, `creator`, `creator_name`, `create_at`, `updater`, `updater_name`, `update_at`, `del_status`) VALUES
('mock_ide_script_08', 'wasmDemo', 'Wasm 示例', 'demo', 'wasm',
NULL, NULL,
'b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4', 1, 'ARCHIVED', 'dev',
'Wasm 模块占位（字节码未上传，wasm_object_name 留空；真实使用时通过插件上传 .wasm 文件后自动填充）', NULL,
'geelato', 'app_demo', 'dev02', '开发者B', NOW(), 'dev02', '开发者B', NOW(), 0);


-- ======================================================================
-- 4. Mock 数据：ide_sync_log（5 条，覆盖各种 action/result）
-- ======================================================================

DELETE FROM `ide_sync_log` WHERE `id` LIKE 'mock_ide_log_%';

INSERT INTO `ide_sync_log` (`id`, `script_id`, `script_code`, `action`, `direction`, `before_hash`, `after_hash`, `before_version`, `after_version`, `operator`, `operator_name`, `tenant_code`, `result`, `message`, `duration_ms`, `client_ip`, `user_agent`, `create_at`) VALUES
('mock_ide_log_01', 'mock_ide_script_02', 'queryUserOrders', 'CREATE', NULL, NULL, 'b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2', NULL, 1, 'admin', '管理员', 'geelato', 'SUCCESS', '从插件创建', 120, '127.0.0.1', 'geelato-vscode/0.2.0', NOW() - INTERVAL 2 DAY),
('mock_ide_log_02', 'mock_ide_script_02', 'queryUserOrders', 'UPDATE', 'FILE_TO_DB', 'b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2', 'b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2', 1, 2, 'admin', '管理员', 'geelato', 'SUCCESS', '插件 push 更新', 89, '127.0.0.1', 'geelato-vscode/0.2.0', NOW() - INTERVAL 1 DAY),
('mock_ide_log_03', 'mock_ide_script_02', 'queryUserOrders', 'UPDATE', 'FILE_TO_DB', 'b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2', 'b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2', 2, 3, 'dev01', '开发者A', 'geelato', 'SUCCESS', '优化分页查询', 156, '192.168.1.100', 'geelato-vscode/0.2.0', NOW() - INTERVAL 2 HOUR),
('mock_ide_log_04', 'mock_ide_script_03', 'fetchWeather', 'DRYRUN', NULL, NULL, NULL, NULL, NULL, 'dev01', '开发者A', 'geelato', 'SUCCESS', 'dry-run 通过（http 调用被沙箱拦截，返回空）', 234, '127.0.0.1', 'geelato-vscode/0.2.0', NOW() - INTERVAL 1 HOUR),
('mock_ide_log_05', 'mock_ide_script_04', 'createOrderDraft', 'DRYRUN', NULL, NULL, NULL, NULL, NULL, 'dev01', '开发者A', 'geelato', 'SUCCESS', 'dry-run 通过（save 已回滚，未落库）', 187, '127.0.0.1', 'geelato-vscode/0.2.0', NOW() - INTERVAL 30 MINUTE);


-- ======================================================================
-- 5. 验证查询
-- ======================================================================

-- 脚本总数
SELECT '== ide_script 总览 ==' AS info;
SELECT language, status, COUNT(*) AS cnt FROM `ide_script` WHERE del_status = 0 GROUP BY language, status ORDER BY language, status;

-- 分组分布
SELECT '== 分组分布 ==' AS info;
SELECT COALESCE(group_name, '(未分组)') AS group_name, COUNT(*) AS cnt FROM `ide_script` WHERE del_status = 0 GROUP BY group_name;

-- 审计日志
SELECT '== ide_sync_log 最近 5 条 ==' AS info;
SELECT script_code, action, result, operator, duration_ms, create_at FROM `ide_sync_log` ORDER BY create_at DESC LIMIT 5;

-- 完成
SELECT '== Mock 数据导入完成：8 条脚本 + 5 条审计日志 ==' AS done;
