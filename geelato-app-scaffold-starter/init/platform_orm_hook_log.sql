SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- Table structure for platform_orm_hook_log
-- ORM 钩子执行发件箱，每次触发一行。
-- afterCommit 回调异步写入（ready）并立即尝试执行一次；失败由调度器扫描（CAS 抢占 processing）、
-- 指数退避重试、超上限死信（dead，可经 /api/ormhook/log/replay/{id} 手动重放）。
-- 执行按 hook_id 回读当前钩子配置（配置被删/禁用则死信）：本表只存触发上下文快照（事件、载荷），
-- 不复制动作配置避免大字段逐行膨胀；修复配置后重放即按新配置执行。
-- 本表读写全部走 JdbcTemplate 直 SQL（不经 ORM save），从根上避免事件递归。
DROP TABLE IF EXISTS `platform_orm_hook_log`;
CREATE TABLE `platform_orm_hook_log`  (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `hook_id` varchar(32) NOT NULL COMMENT '钩子配置ID（执行时回读当前配置）',
  `event_id` varchar(36) NULL DEFAULT NULL COMMENT '触发事件ID（ORM 事件 eventId）',
  `entity_name` varchar(64) NOT NULL COMMENT '实体名称',
  `event_type` varchar(16) NOT NULL COMMENT '事件类型：insert | update | delete',
  `op_type` varchar(16) NULL DEFAULT NULL COMMENT '操作类型：Insert | Update | Delete',
  `action_type` varchar(16) NOT NULL COMMENT '动作类型信息性快照：script | http；实际执行按当前配置',
  `payload_json` mediumtext NULL COMMENT '触发载荷 JSON：values 新值快照、session 会话信息、eventId 等',
  `status` varchar(16) NULL DEFAULT 'ready' COMMENT '执行状态：ready | processing | success | dead',
  `retry_count` int NULL DEFAULT 0 COMMENT '重试次数',
  `next_retry_at` datetime NULL DEFAULT NULL COMMENT '下次重试时间',
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
  INDEX `idx_hook_log_ready`(`status`, `next_retry_at`) USING BTREE COMMENT '调度扫描就绪项',
  INDEX `idx_hook_log_hook`(`hook_id`) USING BTREE COMMENT '按钩子配置追溯执行历史'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'ORM钩子执行发件箱' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
