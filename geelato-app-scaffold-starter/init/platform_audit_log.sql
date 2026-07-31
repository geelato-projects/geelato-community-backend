SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- Table structure for platform_audit_log
-- 平台业务审计日志表
-- 以「业务动作」为一等公民，记录：谁（含委托代理身份）在何时、对哪个业务对象、执行了什么业务动作、产生了什么影响。
-- 产出中文业务摘要（summary），用户可直接看懂；明细（detail_json）可展开查看字段级前后变化。
-- 与 auth 的 audit_event（认证/安全事件）解耦，本表只记录业务实体的变更。
DROP TABLE IF EXISTS `platform_audit_log`;
CREATE TABLE `platform_audit_log`  (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `trace_id` varchar(64) NULL DEFAULT NULL COMMENT '链路追踪ID',
  `request_id` varchar(64) NULL DEFAULT NULL COMMENT '请求ID',
  `operate_at` datetime(3) NULL DEFAULT NULL COMMENT '操作时间',

  `capture_layer` varchar(16) NULL DEFAULT NULL COMMENT '捕获层：ANNOTATED 注解声明 / ORM_FALLBACK 兜底记录',
  `oper_type` varchar(32) NULL DEFAULT NULL COMMENT '动作类型：APPROVE/SUBMIT/REJECT/CREATE/UPDATE/DELETE/EXPORT/CUSTOM',
  `oper_name` varchar(128) NULL DEFAULT NULL COMMENT '业务动作名（中文），如 审批运单 / 修改了 / 删除了',

  `biz_type` varchar(64) NULL DEFAULT NULL COMMENT '业务类型，注解声明或实体名，如 freight_order',
  `entity_title` varchar(128) NULL DEFAULT NULL COMMENT '实体中文名（取自类级 @Title），如 运单',
  `entity_name` varchar(64) NULL DEFAULT NULL COMMENT '实体名（程序用）',
  `table_name` varchar(64) NULL DEFAULT NULL COMMENT '表名（程序用）',
  `target_id` varchar(64) NULL DEFAULT NULL COMMENT '业务对象主键',
  `target_name` varchar(256) NULL DEFAULT NULL COMMENT '业务对象名称（业务编号），如 WBL-2024-001',

  `actor_id` varchar(64) NULL DEFAULT NULL COMMENT '实际操作人ID（代理人）',
  `actor_name` varchar(64) NULL DEFAULT NULL COMMENT '实际操作人名称（代理人）',
  `actor_type` varchar(16) NULL DEFAULT NULL COMMENT '操作人类型：USER/SYSTEM/SCHEDULED/ANONYMOUS',
  `delegator_id` varchar(64) NULL DEFAULT NULL COMMENT '委托人ID（被代理人），无委托则为空',
  `delegator_name` varchar(64) NULL DEFAULT NULL COMMENT '委托人名称（被代理人）',

  `tenant_code` varchar(64) NULL DEFAULT NULL COMMENT '租户编码',
  `org_id` varchar(32) NULL DEFAULT NULL COMMENT '组织ID',
  `dept_id` varchar(32) NULL DEFAULT NULL COMMENT '部门ID',
  `bu_id` varchar(32) NULL DEFAULT NULL COMMENT '业务单元/分公司ID',
  `client_id` varchar(64) NULL DEFAULT NULL COMMENT '客户端ID',
  `session_id` varchar(64) NULL DEFAULT NULL COMMENT '会话ID',
  `ip` varchar(64) NULL DEFAULT NULL COMMENT '操作IP',
  `user_agent` varchar(255) NULL DEFAULT NULL COMMENT 'User-Agent',

  `method` varchar(255) NULL DEFAULT NULL COMMENT '触发方法（类#方法，注解场景）',

  `summary` varchar(1024) NULL DEFAULT NULL COMMENT '业务摘要（核心产出，人话），如 张三 审批了 运单 WBL-2024-001，状态 待审批→已通过',
  `detail_json` longtext NULL COMMENT '数据明细：字段级变更JSON数组（中文标题+前后值+状态码翻译）',
  `metadata` longtext NULL COMMENT '扩展信息JSON（注解 SpEL 业务参数、审批意见等）',

  `duration_ms` int NULL DEFAULT NULL COMMENT '耗时（毫秒）',

  `del_status` int NOT NULL DEFAULT 0 COMMENT '逻辑删除状态，1：已删除、0：未删除',
  `update_at` datetime NOT NULL COMMENT '更新时间',
  `updater` varchar(32) NOT NULL COMMENT '更新者',
  `updater_name` varchar(64) NULL DEFAULT NULL COMMENT '更新者名称',
  `create_at` datetime NOT NULL COMMENT '创建时间',
  `creator` varchar(32) NOT NULL COMMENT '创建者',
  `creator_name` varchar(64) NULL DEFAULT NULL COMMENT '创建者名称',
  `delete_at` datetime NULL DEFAULT NULL COMMENT '删除时间',

  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_actor_time`(`actor_id`, `operate_at`) USING BTREE,
  INDEX `idx_delegator_time`(`delegator_id`, `operate_at`) USING BTREE,
  INDEX `idx_target`(`biz_type`, `target_id`, `operate_at`) USING BTREE,
  INDEX `idx_trace`(`trace_id`) USING BTREE,
  INDEX `idx_tenant_time`(`tenant_code`, `operate_at`) USING BTREE,
  INDEX `idx_oper_time`(`operate_at`) USING BTREE,
  INDEX `idx_biz_type_time`(`biz_type`, `operate_at`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '平台业务审计日志表' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
