SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- Table structure for platform_orm_hook
-- ORM 实体钩子配置：为指定实体配置 insert/update/delete 三类 after-commit 异步钩子。
-- 纯附加特性：业务事务内零额外操作，事务提交后异步触发，执行走发件箱 platform_orm_hook_log（重试/死信），
-- 任何失败不影响业务链路。禁止对本表与 platform_orm_hook_log 配置钩子（防自触发，服务层校验拦截）。
-- 动作类型 v2：script=内嵌脚本（script_content，不经 platform_api）；http=直接调用 HTTP 接口。
-- 发件箱执行按 hook_id 回读当前配置：修改配置对未完成重试立即生效；配置被删/禁用则死信。
DROP TABLE IF EXISTS `platform_orm_hook`;
CREATE TABLE `platform_orm_hook`  (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `title` varchar(128) NOT NULL COMMENT '钩子名称',
  `entity_name` varchar(64) NOT NULL COMMENT '目标实体名称（entityName，须存在于元数据）',
  `event_type` varchar(16) NOT NULL COMMENT '事件类型：insert | update | delete（逻辑删除以 Update 触发 update 事件）',
  `action_type` varchar(16) NOT NULL DEFAULT 'http' COMMENT '动作类型：http（默认，调用HTTP接口）| script（内嵌脚本）',
  `script_content` mediumtext NULL COMMENT 'script 动作脚本内容：function(){...}，触发载荷经闭包中的 parameter 获取',
  `http_method` varchar(16) NULL DEFAULT NULL COMMENT 'http 动作方法：GET | POST | PUT | DELETE | PATCH（默认 POST）',
  `http_url` varchar(512) NULL DEFAULT NULL COMMENT 'http 动作接口地址',
  `http_headers` mediumtext NULL COMMENT 'http 动作请求头 JSON，如 {"Authorization":"Bearer x"}',
  `http_body` mediumtext NULL COMMENT 'http 动作请求体模板，支持 ${路径} 占位符（如 ${values.id}）；留空默认发送完整载荷 JSON',
  `enable_status` int NULL DEFAULT 1 COMMENT '是否启用：1 启用、0 禁用',
  `tenant_code` varchar(32) NULL DEFAULT NULL COMMENT '租户编码（v1 规则为平台级，匹配不区分租户）',
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
  INDEX `idx_hook_entity`(`entity_name`, `event_type`, `enable_status`) USING BTREE COMMENT '按实体+事件匹配规则'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'ORM实体钩子配置' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
