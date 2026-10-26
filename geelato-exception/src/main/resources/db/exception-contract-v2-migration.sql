-- =============================================================================
-- 业务异常预警中心 契约 v2 迁移 DDL（platform_exception）
--
-- 变更内容（2026-10-22 契约去业务化）：
--   删除  customer_id / customer_name（业务概念降级为 hits extras，不落列）
--   改名  biz_no   → biz_label（业务可读标识）
--   改名  hit_brief → title（实例标题，脚本组装）
--   新增  suppress_keys（抑制维度键 JSON 数组，keyFields 泛化机制物化列）
--
-- 二选一执行：方案 A 保留存量实例数据；方案 B DROP 重建（模块未铺开时推荐，零历史顾虑）。
-- 执行前先选定目标库（USE <库名>）。
-- =============================================================================

-- ----------------------------
-- 方案 A：存量保留（MySQL 8.0+ RENAME COLUMN）
-- ----------------------------
ALTER TABLE `platform_exception`
  DROP COLUMN `customer_id`,
  DROP COLUMN `customer_name`,
  CHANGE COLUMN `biz_no` `biz_label` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '业务可读标识（列表/URL 模板占位）',
  CHANGE COLUMN `hit_brief` `title` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '实例标题（脚本组装，兜底规则名）',
  ADD COLUMN `suppress_keys` json NULL COMMENT '抑制维度键（引用本规则的 inhibitedBy 各声明拼键 JSON 数组）' AFTER `title`;

-- ----------------------------
-- 方案 B：DROP 重建（数据可弃时使用；表结构以 exception-schema.sql 为准）
-- ----------------------------
-- DROP TABLE IF EXISTS `platform_exception`;
-- 之后整体执行 db/exception-schema.sql 的第 2 节建表语句即可。
