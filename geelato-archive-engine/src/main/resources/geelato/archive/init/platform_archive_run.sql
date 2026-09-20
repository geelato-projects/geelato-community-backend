SET NAMES utf8mb4;

-- Table structure for platform_archive_run
-- 归档运行记录表：每次策略执行一行，完整记录批次、行数、游标水位与错误上下文，
-- 是"确保归档不误删数据"的审计凭据（失败记录支持精确诊断）。
CREATE TABLE IF NOT EXISTS `platform_archive_run`  (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `policy_id` varchar(32) NOT NULL COMMENT '策略ID',
  `status` varchar(16) NOT NULL COMMENT '状态：running/success/failed/canceled',
  `archived_rows` bigint NOT NULL DEFAULT 0 COMMENT '已归档行数',
  `batches` int NOT NULL DEFAULT 0 COMMENT '批次数',
  `cursor_id` varchar(64) NULL DEFAULT NULL COMMENT '游标（最后一批末位主键）',
  `time_watermark` datetime NULL DEFAULT NULL COMMENT '时间水位（run 开始时锁定的窗口边界）',
  `execution_mode` varchar(16) NULL DEFAULT NULL COMMENT '实际执行模式（AUTO 探测后生效的模式）',
  `cost_ms` bigint NOT NULL DEFAULT 0 COMMENT '耗时毫秒',
  `error_json` longtext NULL COMMENT '错误详情（结构化：表名/批次序号/游标/期望与实际行数/异常堆栈）',
  `begin_at` datetime NULL DEFAULT NULL COMMENT '开始时间',
  `end_at` datetime NULL DEFAULT NULL COMMENT '结束时间',
  `message` varchar(1024) NULL DEFAULT NULL COMMENT '简要信息（成功汇总/失败摘要）',
  `del_status` int NOT NULL DEFAULT 0 COMMENT '逻辑删除状态，1：已删除、0：未删除',
  `update_at` datetime NOT NULL COMMENT '更新时间',
  `updater` varchar(32) NOT NULL COMMENT '更新者',
  `updater_name` varchar(64) NULL DEFAULT NULL COMMENT '更新者名称',
  `create_at` datetime NOT NULL COMMENT '创建时间',
  `creator` varchar(32) NOT NULL COMMENT '创建者',
  `creator_name` varchar(64) NULL DEFAULT NULL COMMENT '创建者名称',
  `delete_at` datetime NULL DEFAULT NULL COMMENT '删除时间',
  `bu_id` varchar(32) NULL DEFAULT NULL COMMENT '单位',
  `dept_id` varchar(32) NULL DEFAULT NULL COMMENT '部门',
  `tenant_code` varchar(32) NULL DEFAULT NULL COMMENT '租户编码',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_policy_id`(`policy_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_begin_at`(`begin_at`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '归档运行记录表' ROW_FORMAT = Dynamic;
