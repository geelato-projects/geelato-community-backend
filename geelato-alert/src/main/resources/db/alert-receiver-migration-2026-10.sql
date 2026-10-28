-- =============================================================================
-- 业务告警中心 接收人模型迁移（2026-10）：废除 receiver_json 的 followSource 标记
--
-- 背景：接收人收敛为规则内完整自包含的选择器定义（{extra:[...]}），引擎只按规则求解，
--   不再投递期回查源实例参与者。followSource=true 且 extra 为空的存量行，
--   就地转为 BIZ_FIELD 选择器（语义最近：命中源实例处理人）。
--   注意：AGGREGATE 形态下原口径是"命中实例参与者并集"，迁移后收窄为"首实例处理人"；
--   如需全员触达，请在规则编辑器中改为显式选择器。
--
-- 用法：存量环境在 alert-schema.sql 基线之上执行本文件；幂等可重复执行。
-- =============================================================================

SET NAMES utf8mb4;

-- 1) followSource=true 且 extra 为空/缺失 → 转为 BIZ_FIELD 选择器（须先于 2) 执行）
UPDATE `platform_alert_rule`
SET receiver_json = JSON_SET(JSON_REMOVE(receiver_json, '$.followSource'),
                             '$.extra', JSON_ARRAY(JSON_OBJECT('type', 'BIZ_FIELD')))
WHERE receiver_json IS NOT NULL
  AND JSON_VALID(receiver_json)
  AND JSON_EXTRACT(receiver_json, '$.followSource') = TRUE
  AND (JSON_LENGTH(receiver_json, '$.extra') IS NULL OR JSON_LENGTH(receiver_json, '$.extra') = 0);

-- 2) 其余行仅剥除 followSource 键
UPDATE `platform_alert_rule`
SET receiver_json = JSON_REMOVE(receiver_json, '$.followSource')
WHERE receiver_json IS NOT NULL
  AND JSON_VALID(receiver_json)
  AND JSON_CONTAINS_PATH(receiver_json, 'one', '$.followSource');
