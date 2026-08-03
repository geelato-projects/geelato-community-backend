SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- Table structure for platform_notification
-- 平台通知主体表，一封通知一行。
-- 与 platform_notification_user（收件人状态）normalized 分离：一封通知可发给多人，每人独立已读状态。
-- 与 platform_notification_outbox（投递发件箱）分离：按渠道异步、可靠、可重试投递。
-- 渠道编排采用可插拔 DeliveryChannel SPI：站内信（inapp）为内置默认渠道，geelato-message（email/sms/wecom）作为独立服务在运行时通过 REST 接入。
DROP TABLE IF EXISTS `platform_notification`;
CREATE TABLE `platform_notification`  (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `title` varchar(128) NOT NULL COMMENT '标题',
  `content` mediumtext NULL COMMENT '内容（可含结构化 JSON 动作）',
  `sender_id` varchar(64) NULL DEFAULT NULL COMMENT '发送者ID，系统发送为 system',
  `sender_name` varchar(64) NULL DEFAULT NULL COMMENT '发送者名称',
  `sender_type` varchar(16) NULL DEFAULT NULL COMMENT '发送者类型：system | user',
  `biz_type` varchar(32) NULL DEFAULT NULL COMMENT '业务类型：order/contract/task 等',
  `biz_id` varchar(64) NULL DEFAULT NULL COMMENT '业务主键',
  `action_url` varchar(512) NULL DEFAULT NULL COMMENT '点击跳转地址，前端 router.push 或 window.open',
  `channels` varchar(256) NULL DEFAULT NULL COMMENT '实际投递渠道快照 JSON，如 ["inapp","email"]',
  `priority` tinyint NULL DEFAULT 0 COMMENT '优先级',
  `tenant_code` varchar(32) NULL DEFAULT NULL COMMENT '租户编码',
  `del_status` int NOT NULL DEFAULT 0 COMMENT '逻辑删除状态，1：已删除、0：未删除',
  `update_at` datetime NOT NULL COMMENT '更新时间',
  `updater` varchar(32) NOT NULL COMMENT '更新者',
  `updater_name` varchar(64) NULL DEFAULT NULL COMMENT '更新者名称',
  `create_at` datetime NOT NULL COMMENT '创建时间',
  `creator` varchar(32) NOT NULL COMMENT '创建者',
  `creator_name` varchar(64) NULL DEFAULT NULL COMMENT '创建者名称',
  `delete_at` datetime NULL DEFAULT NULL COMMENT '删除时间',
  `bu_id` varchar(32) NULL DEFAULT NULL COMMENT '业务单元/分公司ID',
  `dept_id` varchar(32) NULL DEFAULT NULL COMMENT '部门ID',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_notif_biz`(`tenant_code`, `biz_type`, `biz_id`) USING BTREE COMMENT '业务幂等：同一租户同一业务不重复创建'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '平台通知主体' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
