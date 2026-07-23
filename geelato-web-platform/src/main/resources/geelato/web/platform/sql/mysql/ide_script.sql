-- ======================================================================
-- IDE Script & Sync Log 表（M1）
-- 说明：IDE/AI 协作的 GraalJS/Python/Wasm 脚本文件化存储
--   - ide_script：脚本唯一源（与 platform_api 解耦，第一阶段不碰老表）
--   - ide_sync_log：pull/push/dry-run 审计
-- 创建时间：2026-07-21
-- ======================================================================

-- ----------------------------------------------------------------------
-- ide_script
-- ----------------------------------------------------------------------
DROP TABLE IF EXISTS `ide_script`;
CREATE TABLE `ide_script` (
    `id`              VARCHAR(32)  NOT NULL COMMENT '主键',
    `code`            VARCHAR(128) NOT NULL COMMENT '业务编码（文件名主键，租户内唯一）',
    `name`            VARCHAR(128) NOT NULL COMMENT '显示名',
    `group_name`      VARCHAR(64)           DEFAULT NULL COMMENT '分组',
    `language`        VARCHAR(16)  NOT NULL DEFAULT 'js' COMMENT '语言：js/python/wasm',
    `content`         MEDIUMTEXT            DEFAULT NULL COMMENT '脚本正文（JS/Python 源）',
    `wasm_object_name` VARCHAR(256)         DEFAULT NULL COMMENT 'wasm 字节码在 OSS/本地磁盘的 objectName（language=wasm 时，DB 不存二进制）',
    `file_hash`       VARCHAR(64)           DEFAULT NULL COMMENT 'content 的 sha256，冲突检测用',
    `version`         INT          NOT NULL DEFAULT 1    COMMENT '乐观锁版本',
    `status`          VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/ARCHIVED',
    `env_scope`       VARCHAR(64)  NOT NULL DEFAULT 'dev' COMMENT 'dev/staging/prod 适用环境',
    `description`     VARCHAR(512)          DEFAULT NULL COMMENT '描述',
    `default_params`  TEXT                  DEFAULT NULL COMMENT '默认 dry-run 参数 JSON',
    -- 标准审计字段（与 BaseEntity 保持一致）
    `tenant_code`     VARCHAR(32)           DEFAULT NULL,
    `app_id`          VARCHAR(32)           DEFAULT NULL,
    `bu_id`           VARCHAR(32)           DEFAULT NULL,
    `dept_id`         VARCHAR(32)           DEFAULT NULL,
    `creator`         VARCHAR(32)           DEFAULT NULL,
    `creator_name`    VARCHAR(64)           DEFAULT NULL,
    `create_at`       DATETIME              DEFAULT NULL,
    `updater`         VARCHAR(32)           DEFAULT NULL,
    `updater_name`    VARCHAR(64)           DEFAULT NULL,
    `update_at`       DATETIME              DEFAULT NULL,
    `delete_at`       DATETIME              DEFAULT NULL,
    `del_status`      INT          NOT NULL DEFAULT 0    COMMENT '逻辑删除：1=已删除，0=未删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code_tenant` (`code`, `tenant_code`),
    KEY `idx_group_name` (`group_name`),
    KEY `idx_status` (`status`),
    KEY `idx_language` (`language`),
    KEY `idx_creator` (`creator`),
    KEY `idx_tenant_status` (`tenant_code`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IDE 脚本（JS/Python/Wasm）';


-- ----------------------------------------------------------------------
-- ide_sync_log
-- ----------------------------------------------------------------------
DROP TABLE IF EXISTS `ide_sync_log`;
CREATE TABLE `ide_sync_log` (
    `id`              VARCHAR(32)  NOT NULL COMMENT '主键',
    `script_id`       VARCHAR(32)  NOT NULL COMMENT '脚本主键',
    `script_code`     VARCHAR(128) NOT NULL COMMENT '脚本业务编码',
    `action`          VARCHAR(16)  NOT NULL COMMENT 'PULL/PUSH/DRYRUN/CREATE/UPDATE/DELETE/PUBLISH',
    `direction`       VARCHAR(16)           DEFAULT NULL COMMENT 'FILE_TO_DB/DB_TO_FILE',
    `before_hash`     VARCHAR(64)           DEFAULT NULL COMMENT '操作前 content hash',
    `after_hash`      VARCHAR(64)           DEFAULT NULL COMMENT '操作后 content hash',
    `before_version`  INT                   DEFAULT NULL,
    `after_version`   INT                   DEFAULT NULL,
    `operator`        VARCHAR(32)  NOT NULL COMMENT '操作人 userId',
    `operator_name`   VARCHAR(64)           DEFAULT NULL,
    `tenant_code`     VARCHAR(32)           DEFAULT NULL,
    `result`          VARCHAR(16)  NOT NULL COMMENT 'SUCCESS/FAIL/REJECTED',
    `message`         VARCHAR(512)          DEFAULT NULL COMMENT '结果说明或错误信息',
    `duration_ms`     BIGINT                DEFAULT NULL COMMENT '耗时（毫秒）',
    `client_ip`       VARCHAR(64)           DEFAULT NULL,
    `user_agent`      VARCHAR(256)          DEFAULT NULL,
    `create_at`       DATETIME     NOT NULL COMMENT '记录时间',
    PRIMARY KEY (`id`),
    KEY `idx_script_id` (`script_id`),
    KEY `idx_script_code` (`script_code`),
    KEY `idx_action_result` (`action`, `result`),
    KEY `idx_operator` (`operator`),
    KEY `idx_tenant_create` (`tenant_code`, `create_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IDE 同步与操作审计';
