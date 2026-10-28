-- =============================================================================
-- 业务告警中心 存量环境重建（2026-10）
--
-- 背景：告警规则与 exception 解耦——match_json 以 source 自描述事件源、
--   platform_alert_record.exception_count 更名 event_count 等列名/注释调整；
--   表 platform_alert_record 更名 platform_alert（与 platform_exception 同构命名）。
--   匹配/聚合/投递引擎未实现、无运行数据，按 DROP 重建处理（规则可由 bea/alert-rules 种子重导）。
--
-- 用法：存量环境先执行本文件（DROP 旧名+新名共 5 表），再执行 alert-schema.sql（新基线重建）。
-- =============================================================================

SET NAMES utf8mb4;

DROP TABLE IF EXISTS `platform_alert_record`;
DROP TABLE IF EXISTS `platform_alert`;
DROP TABLE IF EXISTS `platform_alert_silence`;
DROP TABLE IF EXISTS `platform_alert_template`;
DROP TABLE IF EXISTS `platform_alert_rule`;
