-- =============================================================================
-- 业务异常预警中心 终身一条/重开模式 迁移 DDL（2026-10-30）
--
-- 行为变更（无结构变更，仅 scan_log 加计数列）：
--   platform_exception.active_fingerprint 由「在途唯一、终态置 NULL」转为「终身唯一常驻」：
--     - 关闭（AUTO/MANUAL/IGNORE/GROUP）不再置空指纹，同一 规则|业务主体|人 终身一条记录；
--     - 终态（RESOLVED/IGNORED）为暂时态，规则再命中时引擎重开原记录（REOPEN 流水 + EXC_REOPENED 事件），
--       first_at 重置为本轮段起点；存量终态行指纹为 NULL，同样可被重开并回填，无需数据迁移；
--   恢复冷却基准收窄：仅引擎 AUTO 关闭计冷却（storm_json.cooldownMinutes），
--     用户消除（MANUAL/IGNORE）与组联动（GROUP）后规则再命中即刻重开。
--
-- 结构变更：
--   platform_exception_scan_log 新增 instance_reopened（本轮重开实例数）
--
-- 执行前先选定目标库（USE <库名>）；列已存在时跳过本 ALTER 即可。
-- =============================================================================

ALTER TABLE `platform_exception_scan_log`
  ADD COLUMN `instance_reopened` int NOT NULL DEFAULT 0 COMMENT '本轮重开实例数' AFTER `instance_renewed`;
