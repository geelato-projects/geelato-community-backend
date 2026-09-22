SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- Table structure for platform_orm_hook
-- ORM 实体钩子配置：一条 Hook = 名称 + 地址 + 启停。
-- 平台内任何实体的 insert/update/delete 事务提交后，向地址 POST 完整载荷 JSON
-- （entityName 实体名称、eventType 事件类型、values 数据变更明细，另附 tableName/eventId/session/firedAt），
-- 下游自行按 entityName/eventType 分发处理。
-- 纯附加特性：业务事务内零额外操作，事务提交后异步触发，执行走发件箱 platform_orm_hook_log（重试/死信），
-- 任何失败不影响业务链路。钩子机制自身两张表不触发（防递归，监听器硬排除）。
-- 执行时按 hook_id 回读当前配置：修改配置对未完成重试立即生效；配置被删/禁用则死信。
DROP TABLE IF EXISTS `platform_orm_hook`;
CREATE TABLE `platform_orm_hook`  (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `title` varchar(128) NOT NULL COMMENT '钩子名称',
  `http_url` varchar(512) NOT NULL COMMENT '下游接收地址：任何实体事件提交后 POST 完整载荷 JSON',
  `enable_status` int NULL DEFAULT 1 COMMENT '是否启用：1 启用、0 禁用',
  `tenant_code` varchar(32) NULL DEFAULT NULL COMMENT '租户编码（钩子为平台级，触发不区分租户）',
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
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'ORM实体钩子配置' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
