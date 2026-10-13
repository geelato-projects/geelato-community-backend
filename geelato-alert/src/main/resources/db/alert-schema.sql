-- =============================================================================
-- 业务告警中心 完整建表 DDL（合并 V161 基线与 V164 引擎版本增量）
--
-- 内容：
--   1. 建表 platform_alert_rule（告警规则库：匹配 + 触发 + 聚合 + 接收人 + 路由 + 模板绑定）
--   2. 建表 platform_alert_template（告警消息模板：js 脚本 + 渠道变体 + 示例上下文 + 引擎版本）
--   3. 建表 platform_alert_record（告警记录：聚合缓冲 / 渲染快照 / 投递状态 / 命中清单）
--   4. 建表 platform_alert_silence（静默规则：维度匹配 + 时间窗）
--
-- 约定：
--   - id 平台惯例 varchar(32)；JSON 列 json；active_group_key 可空唯一（组内未闭环占用、闭环置 NULL）
--   - platform_alert_template.engine_version 为脚本契约快照（空=v1）
--
-- 幂等：CREATE TABLE IF NOT EXISTS，重复执行安全。
-- =============================================================================

-- 执行前先选定目标库（USE <库名>），本脚本不带库名前缀、标准 ; 分隔，可直接在 MySQL 客户端整体执行。
SET NAMES utf8mb4;

-- ----------------------------
-- 1. platform_alert_rule（告警规则表）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `platform_alert_rule` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
  `alert_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '告警规则编码 ALR-{三位序号}',
  `alert_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '告警名称',
  `alert_desc` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '描述',
  `match_json` json NOT NULL COMMENT '异常匹配器 {eventTypes,ruleCodes}',
  `trigger_mode` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'EVENT' COMMENT 'EVENT/SCHEDULE/BOTH',
  `cron_expression` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'SCHEDULE/BOTH 周期',
  `dataset_json` json NULL COMMENT 'SCHEDULE 数据集口径',
  `aggregate_mode` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'INDEPENDENT' COMMENT 'INDEPENDENT/AGGREGATE',
  `group_wait_seconds` int NULL DEFAULT NULL COMMENT '聚合窗口秒',
  `group_interval_seconds` int NULL DEFAULT NULL COMMENT '再通知最小间隔秒',
  `repeat_interval_minutes` int NULL DEFAULT NULL COMMENT '在途组重复提醒间隔分钟',
  `max_items` int NULL DEFAULT NULL COMMENT '清单渲染条数上限',
  `resolve_notify` tinyint(1) NOT NULL DEFAULT 0 COMMENT '全部闭环是否发恢复通知',
  `receiver_json` json NOT NULL COMMENT '接收人 {followException,extra[]}',
  `route_json` json NOT NULL COMMENT '通道路由 {default:{channels,templateCode}}',
  `daily_limit` int NULL DEFAULT NULL COMMENT '每日发送上限条',
  `enable_status` tinyint(1) NOT NULL DEFAULT 0 COMMENT '0停用/1生效',
  `version` int NOT NULL DEFAULT 1 COMMENT '口径版本',
  `seq_no` int NULL DEFAULT 0 COMMENT '排序',
  `app_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '所属应用ID（platform_app.id）',
  `tenant_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '租户编码',
  `dept_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '部门',
  `bu_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '单位',
  `del_status` int NOT NULL DEFAULT 0 COMMENT '逻辑删除状态，1：已删除、0：未删除',
  `update_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `updater` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新者',
  `updater_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新者名称',
  `create_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `creator` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者',
  `creator_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者名称',
  `delete_at` datetime NULL DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_alert_rule_code` (`alert_code` ASC, `del_status` ASC, `delete_at` ASC) USING BTREE,
  INDEX `idx_alert_rule_enable` (`enable_status` ASC, `trigger_mode` ASC) USING BTREE,
  INDEX `idx_alert_rule_tenant` (`tenant_code` ASC, `del_status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '业务告警中心-告警规则表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 2. platform_alert_template（告警消息模板表，UK(template_code, channel)）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `platform_alert_template` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
  `template_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模板编码 AT-{三位序号}',
  `template_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模板名称',
  `channel` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'default' COMMENT '渠道变体 default/inapp/email/sms/wework/bot',
  `template_desc` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '用途说明',
  `title_script` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '标题渲染脚本（js）',
  `content_script` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '正文渲染脚本（js）',
  `context_sample_json` json NULL COMMENT '示例上下文',
  `context_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'SINGLE/GROUP/SCHEDULE',
  `version` int NOT NULL DEFAULT 1 COMMENT '版本',
  `engine_version` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '引擎版本（脚本契约快照，空=v1）',
  `enable_status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '0停用/1生效',
  `seq_no` int NULL DEFAULT 0 COMMENT '排序',
  `app_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '所属应用ID（platform_app.id）',
  `tenant_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '租户编码',
  `dept_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '部门',
  `bu_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '单位',
  `del_status` int NOT NULL DEFAULT 0 COMMENT '逻辑删除状态，1：已删除、0：未删除',
  `update_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `updater` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新者',
  `updater_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新者名称',
  `create_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `creator` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者',
  `creator_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者名称',
  `delete_at` datetime NULL DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_alert_tpl_code_channel` (`template_code` ASC, `channel` ASC, `del_status` ASC, `delete_at` ASC) USING BTREE,
  INDEX `idx_alert_tpl_enable` (`enable_status` ASC) USING BTREE,
  INDEX `idx_alert_tpl_tenant` (`tenant_code` ASC, `del_status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '业务告警中心-告警消息模板表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 3. platform_alert_record（告警记录表）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `platform_alert_record` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
  `alert_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '告警规则编码',
  `alert_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '冗余名',
  `alert_version` int NULL DEFAULT NULL COMMENT '规则口径版本',
  `trigger_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'EVENT/SCHEDULE/REPEAT/RESOLVE',
  `event_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '触发异常事件类型',
  `batch_no` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '批次号',
  `group_key` varchar(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '聚合身份',
  `active_group_key` varchar(180) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '在途告警组唯一列，闭环置 NULL',
  `exception_count` int NOT NULL DEFAULT 0 COMMENT '命中异常数',
  `event_ids` json NULL COMMENT '命中异常实例 id 清单',
  `stats_json` json NULL COMMENT '聚合统计快照',
  `rendered_title` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '渲染快照标题',
  `rendered_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '渲染快照正文',
  `receiver_ids` json NULL COMMENT '接收人清单',
  `receiver_names` json NULL COMMENT '接收人名称清单',
  `channels` json NULL COMMENT '实际投递通道组',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/READY/SENT/PARTIAL/FAIL/SILENCED',
  `silence_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'SILENCED 命中的静默规则 id',
  `flush_at` datetime NULL DEFAULT NULL COMMENT 'PENDING 缓冲冲刷到期时刻',
  `first_event_at` datetime NULL DEFAULT NULL COMMENT '组内首事件时刻',
  `last_event_at` datetime NULL DEFAULT NULL COMMENT '组内末事件时刻',
  `last_sent_at` datetime NULL DEFAULT NULL COMMENT '最近发送时刻',
  `biz_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'geelato-message 关联回查键',
  `retry_count` int NOT NULL DEFAULT 0 COMMENT '投递重试次数',
  `error_message` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '错误信息',
  `sent_at` datetime NULL DEFAULT NULL COMMENT '首次成功投递时刻',
  `seq_no` int NULL DEFAULT 0 COMMENT '排序',
  `app_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '所属应用ID（platform_app.id）',
  `tenant_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '租户编码',
  `dept_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '部门',
  `bu_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '单位',
  `del_status` int NOT NULL DEFAULT 0 COMMENT '逻辑删除状态，1：已删除、0：未删除',
  `update_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `updater` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新者',
  `updater_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新者名称',
  `create_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `creator` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者',
  `creator_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者名称',
  `delete_at` datetime NULL DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_alert_active_group` (`active_group_key` ASC) USING BTREE,
  INDEX `idx_alert_record_rule` (`alert_code` ASC, `status` ASC) USING BTREE,
  INDEX `idx_alert_record_group` (`group_key` ASC, `create_at` ASC) USING BTREE,
  INDEX `idx_alert_record_flush` (`flush_at` ASC) USING BTREE,
  INDEX `idx_alert_record_create` (`create_at` ASC) USING BTREE,
  INDEX `idx_alert_record_tenant` (`tenant_code` ASC, `del_status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '业务告警中心-告警记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 4. platform_alert_silence（静默规则表）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `platform_alert_silence` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
  `silence_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '静默名称',
  `match_json` json NOT NULL COMMENT '压制维度 {alertCodes,ruleCodes,customerIds}',
  `window_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ONCE' COMMENT 'ONCE/RECURRING',
  `start_at` datetime NULL DEFAULT NULL COMMENT 'ONCE 起 / RECURRING 生效总区间起',
  `end_at` datetime NULL DEFAULT NULL COMMENT 'ONCE 止 / RECURRING 生效总区间止',
  `time_window` json NULL COMMENT 'RECURRING 定义 {days,startTime,endTime}',
  `reason` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '静默原因',
  `allow_resolve` tinyint(1) NOT NULL DEFAULT 1 COMMENT '恢复通知豁免，1不受压制',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/EXPIRED/REVOKED',
  `seq_no` int NULL DEFAULT 0 COMMENT '排序',
  `tenant_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '租户编码',
  `dept_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '部门',
  `bu_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '单位',
  `del_status` int NOT NULL DEFAULT 0 COMMENT '逻辑删除状态，1：已删除、0：未删除',
  `update_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `updater` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新者',
  `updater_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新者名称',
  `create_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `creator` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者',
  `creator_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者名称',
  `delete_at` datetime NULL DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_alert_silence_status` (`status` ASC, `end_at` ASC) USING BTREE,
  INDEX `idx_alert_silence_tenant` (`tenant_code` ASC, `del_status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '业务告警中心-静默规则表' ROW_FORMAT = Dynamic;
