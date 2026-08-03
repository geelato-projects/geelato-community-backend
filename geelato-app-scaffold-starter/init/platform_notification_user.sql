SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- Table structure for platform_notification_user
-- 通知收件人状态表，每个收件人一行。
-- 与 platform_notification（主体）normalized 分离，使每人独立已读/星标/归档状态，互不影响。
-- 撤回通知 = 逻辑删主体，对应全员收件箱同步消失（关联主体查询）。
DROP TABLE IF EXISTS `platform_notification_user`;
CREATE TABLE `platform_notification_user`  (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `notification_id` varchar(32) NOT NULL COMMENT '通知主体ID',
  `user_id` varchar(64) NOT NULL COMMENT '收件人ID',
  `read_status` tinyint NULL DEFAULT 0 COMMENT '已读状态，0：未读、1：已读',
  `read_at` datetime NULL DEFAULT NULL COMMENT '已读时间',
  `starred` tinyint NULL DEFAULT 0 COMMENT '星标，0：否、1：是',
  `archived` tinyint NULL DEFAULT 0 COMMENT '已归档，0：否、1：是',
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
  UNIQUE INDEX `uk_notif_user`(`notification_id`, `user_id`) USING BTREE COMMENT '同一通知同一收件人不重复',
  INDEX `idx_user_unread`(`user_id`, `read_status`, `archived`) USING BTREE COMMENT '收件箱未读查询',
  INDEX `idx_notif`(`notification_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '通知收件人状态' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
