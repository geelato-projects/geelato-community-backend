-- ============================================================
-- 邮件同步本地化 - PostgreSQL DDL
-- 数据源通过 application.properties (geelato.email.datasource.*) 配置
-- 由 EmailDataSourceConfig 自动注册，无需操作 platform_dev_db_connect 表
-- ============================================================

-- 1. 邮件消息表
CREATE TABLE IF NOT EXISTS platform_email_message (
    id               VARCHAR(64)  PRIMARY KEY,
    email_account_id VARCHAR(64)  NOT NULL,
    user_id          VARCHAR(64)  NOT NULL,
    folder           VARCHAR(255) NOT NULL,
    uid              BIGINT,
    uid_validity     BIGINT,
    message_id       VARCHAR(512),
    subject          VARCHAR(1000),
    from_json        TEXT,
    to_json          TEXT,
    cc_json          TEXT,
    bcc_json         TEXT,
    sent_at          TIMESTAMP,
    received_at      TIMESTAMP,
    size             BIGINT,
    unread           SMALLINT     DEFAULT 0,
    has_attachments  SMALLINT     DEFAULT 0,
    text_body        TEXT,
    html_body        TEXT,
    snippet          VARCHAR(500),
    synced_at        TIMESTAMP,
    create_at        TIMESTAMP    NOT NULL,
    creator          VARCHAR(64),
    creator_name     VARCHAR(128),
    bu_id            VARCHAR(32),
    dept_id          VARCHAR(32),
    tenant_code      VARCHAR(32),
    update_at        TIMESTAMP    NOT NULL,
    updater          VARCHAR(64),
    updater_name     VARCHAR(128),
    delete_at        TIMESTAMP,
    del_status       INT          DEFAULT 0
);

-- 唯一约束：同一邮箱账号+文件夹+UIDVALIDITY+UID 不重复（软删除通过 delete_at 区分）
CREATE UNIQUE INDEX IF NOT EXISTS uk_email_msg_account_folder_uid
    ON platform_email_message (email_account_id, folder, uid_validity, uid, del_status, delete_at);

-- 查询索引
CREATE INDEX IF NOT EXISTS idx_email_msg_account_folder
    ON platform_email_message (email_account_id, folder, received_at DESC);
CREATE INDEX IF NOT EXISTS idx_email_msg_user
    ON platform_email_message (user_id, received_at DESC);


-- 2. 邮件附件表
CREATE TABLE IF NOT EXISTS platform_email_attachment (
    id                VARCHAR(64)  PRIMARY KEY,
    email_message_id  VARCHAR(64)  NOT NULL,
    part_id           VARCHAR(32),
    file_name         VARCHAR(512),
    content_type      VARCHAR(255),
    size              BIGINT,
    inline            SMALLINT     DEFAULT 0,
    content_id        VARCHAR(255),
    attachment_id     VARCHAR(64),
    oss_path          VARCHAR(1024),
    create_at         TIMESTAMP    NOT NULL,
    creator           VARCHAR(64),
    creator_name      VARCHAR(128),
    bu_id             VARCHAR(32),
    dept_id           VARCHAR(32),
    tenant_code       VARCHAR(32),
    update_at         TIMESTAMP    NOT NULL,
    updater           VARCHAR(64),
    updater_name      VARCHAR(128),
    delete_at         TIMESTAMP,
    del_status        INT          DEFAULT 0
);

-- 查询索引
CREATE INDEX IF NOT EXISTS idx_email_att_message
    ON platform_email_attachment (email_message_id);


-- 3. 邮件同步日志表
CREATE TABLE IF NOT EXISTS platform_email_sync_log (
    id                VARCHAR(64)  PRIMARY KEY,
    email_account_id  VARCHAR(64)  NOT NULL,
    folder            VARCHAR(255),
    sync_type         VARCHAR(32),
    status            VARCHAR(32),
    last_uid          BIGINT,
    synced_count      INT,
    error_message     TEXT,
    start_at          TIMESTAMP,
    end_at            TIMESTAMP,
    create_at         TIMESTAMP    NOT NULL,
    creator           VARCHAR(64),
    creator_name      VARCHAR(128),
    bu_id             VARCHAR(32),
    dept_id           VARCHAR(32),
    tenant_code       VARCHAR(32),
    update_at         TIMESTAMP    NOT NULL,
    updater           VARCHAR(64),
    updater_name      VARCHAR(128),
    delete_at         TIMESTAMP,
    del_status        INT          DEFAULT 0
);

-- 查询索引
CREATE INDEX IF NOT EXISTS idx_email_sync_log_account
    ON platform_email_sync_log (email_account_id, start_at DESC);


-- ============================================================
-- MySQL 主库: 给 platform_user_email_account 表增加同步字段
-- ============================================================
-- ALTER TABLE platform_user_email_account
--     ADD COLUMN sync_enabled           TINYINT      DEFAULT 0   COMMENT '同步开关 0关1开',
--     ADD COLUMN sync_interval_minutes  INT          DEFAULT 5   COMMENT '同步间隔(分钟)',
--     ADD COLUMN last_sync_at           DATETIME                 COMMENT '上次同步时间',
--     ADD COLUMN sync_status            VARCHAR(32)  DEFAULT 'idle' COMMENT '同步状态 idle/syncing/error';
