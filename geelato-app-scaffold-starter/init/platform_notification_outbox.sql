SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- Table structure for platform_notification_outbox
-- 通知投递编排发件箱，每个投递渠道一行。
-- 事务性 outbox：业务事务内写入（ready），由调度器异步扫描、抢占（CAS→processing）、调用对应 DeliveryChannel 投递，
-- 成功→success，失败→指数退避重试→死信（dead）。单渠道失败不影响其他渠道。
-- 站内信渠道（inapp）由通知中心自行完成（写 notification_user + SSE 推送）；
-- 外部渠道（email/sms/wecom）由 GeelatoMessageChannel 通过 REST 委托给独立运行的 geelato-message 服务，零编译期依赖。
DROP TABLE IF EXISTS `platform_notification_outbox`;
CREATE TABLE `platform_notification_outbox`  (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `notification_id` varchar(32) NOT NULL COMMENT '通知主体ID',
  `channel` varchar(32) NOT NULL COMMENT '投递渠道：inapp | email | sms | wecom ...',
  `recipient_json` varchar(2048) NOT NULL COMMENT '收件人列表 JSON，如 ["u1","u2"]',
  `status` varchar(16) NULL DEFAULT 'ready' COMMENT '投递状态：ready | processing | success | fail | dead',
  `retry_count` int NULL DEFAULT 0 COMMENT '重试次数',
  `next_retry_at` datetime NULL DEFAULT NULL COMMENT '下次重试时间',
  `idempotency_key` varchar(128) NULL DEFAULT NULL COMMENT '幂等键',
  `error_msg` varchar(512) NULL DEFAULT NULL COMMENT '错误信息',
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
  UNIQUE INDEX `uk_outbox_idem`(`tenant_code`, `idempotency_key`) USING BTREE COMMENT '投递幂等',
  INDEX `idx_outbox_ready`(`status`, `next_retry_at`) USING BTREE COMMENT '调度扫描就绪项',
  INDEX `idx_outbox_notif`(`notification_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '通知投递发件箱' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
