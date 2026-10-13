-- =============================================================================
-- 业务异常预警中心 完整建表 DDL（合并 V159 基线与 V164 扫描通道增量）
--
-- 内容：
--   1. 建表 platform_exception_rule（异常规则库，判定定义 + 参数 + 触发/参与者/处置配置 + 扫描抢占）
--   2. 建表 platform_exception（告警实例，一人一实例，参与者实例模型）
--   3. 建表 platform_exception_step（处置步骤）
--   4. 建表 platform_exception_handle（处理轨迹流水，只追加）
--   6. 建表 platform_exception_scan_log（扫描执行留痕）
--
-- 约定：
--   - id 采用平台惯例 varchar(32)（设计文档「bigint PK」为概念口径，落地统一平台主键）
--   - JSON 列用 MySQL json；时间 datetime；enable_status/resolve_* 等用 tinyint(1)
--   - active_fingerprint 可空唯一列：在途非空、终态置 NULL（MySQL 唯一索引允许多 NULL）
--   - 审计列沿用平台惯例（creator/create_at/update_at/updater/del_status/dept_id/bu_id/tenant_code）
--   - platform_exception_rule.scan_status/scan_started_at/scan_runner 为集群扫描防重 CAS 列
--     （UPDATE ... WHERE scan_status=0 OR scan_started_at<stale），engine_version 为脚本契约快照
--   - 实体元数据经 @Entity 注解由 Geelato ORM 运行期注册（不落 scaffold init SQL）
--
-- 幂等：CREATE TABLE IF NOT EXISTS，重复执行安全。
-- =============================================================================

-- 执行前先选定目标库（USE <库名>），本脚本不带库名前缀、标准 ; 分隔，可直接在 MySQL 客户端整体执行。
SET NAMES utf8mb4;

-- ----------------------------
-- 1. platform_exception_rule（异常规则表）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `platform_exception_rule` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
  `rule_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '规则编码 EXC-{域码}-{三位序号}',
  `rule_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '规则名称',
  `domain_code` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '业务域 ODR/BL/FIN/CUS/SLOT/QLT/...',
  `rule_desc` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '规则统一描述（范围/判定/恢复三段）',
  `level` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '等级 P0~P3',
  `rule_kind` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ALERT' COMMENT 'ALERT 告警规则 / ESCALATE 升级规则',
  `escalation_of_rule_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'ESCALATE 关联的基础/上一级规则编码',
  `trigger_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'SCAN' COMMENT 'CHANGE/SCAN/BOTH',
  `cron_expression` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '扫描周期',
  `listen_entities` json NULL COMMENT '订阅入口实体 [{entity,operations,role,refField,fields}]',
  `pipeline_json` json NULL COMMENT '判定管道 {steps:[{name,lang,content}]}',
  `threshold_json` json NULL COMMENT '规则参数（$params 消费）',
  `owner_selector` json NULL COMMENT '参与者选择器 {handlers:[],cc:[]}',
  `resolve_policy` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ANY' COMMENT 'ANY/ALL/SELF',
  `storm_json` json NULL COMMENT '风暴抑制配置 {hitLimit,overflowPolicy,cooldownMinutes,inhibitedBy}',
  `biz_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '主关联业务对象类型',
  `mutual_exclusive_group` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '衔接互斥组',
  `flow_template` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'STANDARD/INTERCEPT/NOTICE',
  `biz_url_template` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '去处理跳转模板',
  `enable_status` tinyint(1) NOT NULL DEFAULT 0 COMMENT '0未启用/1生效',
  `scan_status` tinyint(1) NOT NULL DEFAULT 0 COMMENT '扫描抢占 0空闲/1执行中',
  `scan_started_at` datetime NULL DEFAULT NULL COMMENT '本轮扫描开始时刻',
  `scan_runner` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '执行节点标识',
  `engine_version` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '引擎版本（脚本契约快照，空=v1）',
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
  UNIQUE INDEX `uk_exc_rule_code` (`rule_code` ASC, `del_status` ASC, `delete_at` ASC) USING BTREE,
  INDEX `idx_exc_rule_enable` (`enable_status` ASC, `trigger_type` ASC) USING BTREE,
  INDEX `idx_exc_rule_tenant` (`tenant_code` ASC, `del_status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '业务异常预警中心-异常规则表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 2. platform_exception（异常实例表，一人一实例）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `platform_exception` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
  `rule_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '规则编码',
  `rule_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '冗余规则名',
  `domain_code` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '冗余域',
  `level` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '本实例等级 P0~P3',
  `active_fingerprint` varchar(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '在途唯一列，终态置 NULL',
  `source_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '同源组键 {rule_code}|{biz_type}|{biz_id}',
  `participant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '本实例处理人',
  `participant_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '处理人名称',
  `participant_org` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '处理人组织',
  `biz_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '业务对象类型',
  `biz_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '业务对象 id',
  `biz_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '业务单号',
  `customer_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '客户 id',
  `customer_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '客户名称',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/PROCESSING/RESOLVED/IGNORED/ESCALATED',
  `current_step_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '当前 ACTIVE 步骤',
  `escalation_level` int NOT NULL DEFAULT 0 COMMENT '0基础/1..n升级链位置',
  `first_at` datetime NULL DEFAULT NULL COMMENT '首次触发',
  `last_seen_at` datetime NULL DEFAULT NULL COMMENT '最近命中续期',
  `hit_count` int NOT NULL DEFAULT 0 COMMENT '累计命中',
  `anchor_elapsed` bigint NULL DEFAULT NULL COMMENT '触发时锚点时长（秒）',
  `hit_brief` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '命中摘要',
  `detail_json` json NULL COMMENT '触发时刻快照',
  `resolved_at` datetime NULL DEFAULT NULL COMMENT '关闭时刻',
  `resolved_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'AUTO/MANUAL/EVENT/GROUP',
  `rule_version` int NULL DEFAULT NULL COMMENT '实例化时规则版本',
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
  UNIQUE INDEX `uk_exc_active_fp` (`active_fingerprint` ASC) USING BTREE,
  INDEX `idx_exc_source` (`source_key` ASC, `status` ASC) USING BTREE,
  INDEX `idx_exc_participant` (`participant_id` ASC, `status` ASC) USING BTREE,
  INDEX `idx_exc_status_level` (`status` ASC, `level` ASC) USING BTREE,
  INDEX `idx_exc_domain` (`domain_code` ASC, `status` ASC) USING BTREE,
  INDEX `idx_exc_biz` (`biz_type` ASC, `biz_id` ASC) USING BTREE,
  INDEX `idx_exc_tenant` (`tenant_code` ASC, `del_status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '业务异常预警中心-异常实例表（一人一实例）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 3. platform_exception_step（处置步骤表）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `platform_exception_step` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
  `exception_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '实例 FK',
  `step_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'CLAIM/HANDLE/INTERCEPT/REVIEW/VERIFY',
  `step_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '步骤名称',
  `seq` int NOT NULL DEFAULT 0 COMMENT '序号',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACTIVE/DONE/TERMINATED',
  `resolver` json NULL COMMENT '本步骤参与者选择器',
  `participants` json NULL COMMENT '求解结果',
  `assigned_at` datetime NULL DEFAULT NULL COMMENT '指派时刻（MTTA）',
  `done_at` datetime NULL DEFAULT NULL COMMENT '完成时刻（MTTR）',
  `timeout_at` datetime NULL DEFAULT NULL COMMENT '预计超时时刻',
  `escalate_count` int NOT NULL DEFAULT 0 COMMENT '本实例经历升级次数',
  `done_action` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'CLAIMED/APPROVED/REJECTED/SKIPPED...',
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
  INDEX `idx_exc_step_exc` (`exception_id` ASC, `seq` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '业务异常预警中心-处置步骤表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 4. platform_exception_handle（处理轨迹流水表，只追加）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `platform_exception_handle` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
  `exception_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '实例 FK',
  `step_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '步骤 FK',
  `action` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'CREATE/CLAIM/HANDLE_NOTE/RESOLVE/IGNORE/AUTO_RECOVER/ESCALATE/ESCALATED/ACK/GROUP_CLOSE/STEP_DONE...',
  `operator_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'SYSTEM/USER',
  `operator_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '操作人 id',
  `operator_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '操作人名称',
  `before_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '变更前状态',
  `after_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '变更后状态',
  `reason_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'IGNORE 必填原因码',
  `seq_no` int NULL DEFAULT 0 COMMENT '排序',
  `remark` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '处理备注',
  `tenant_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '租户编码',
  `dept_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '部门',
  `bu_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '单位',
  `del_status` int NOT NULL DEFAULT 0 COMMENT '逻辑删除状态，1：已删除、0：未删除',
  `update_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `updater` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新者',
  `updater_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新者名称',
  `create_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（时间线排序键）',
  `creator` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者',
  `creator_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者名称',
  `delete_at` datetime NULL DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_exc_handle_exc` (`exception_id` ASC, `create_at` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '业务异常预警中心-处理轨迹流水表' ROW_FORMAT = Dynamic;


-- ----------------------------
-- 5. platform_exception_scan_log（扫描执行留痕表）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `platform_exception_scan_log` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
  `rule_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '规则编码',
  `trigger_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'SCAN/RECHECK/TRIAL',
  `start_at` datetime NULL DEFAULT NULL COMMENT '开始时刻',
  `finish_at` datetime NULL DEFAULT NULL COMMENT '结束时刻',
  `candidate_count` int NOT NULL DEFAULT 0 COMMENT '候选量',
  `hit_count` int NOT NULL DEFAULT 0 COMMENT '判定命中数',
  `instance_created` int NOT NULL DEFAULT 0 COMMENT '本轮新建实例数',
  `instance_renewed` int NOT NULL DEFAULT 0 COMMENT '本轮续期实例数',
  `instance_recovered` int NOT NULL DEFAULT 0 COMMENT '本轮恢复实例数',
  `suppressed_count` int NOT NULL DEFAULT 0 COMMENT '本轮抑制数',
  `cost_ms` int NOT NULL DEFAULT 0 COMMENT '耗时（毫秒）',
  `steps_json` json NULL COMMENT '管道步骤明细 [{name,lang,status,costMs,rows,console}]',
  `error` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '失败留痕',
  `event_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '事件接入幂等键（entity-event 去重）',
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
  UNIQUE INDEX `uk_exc_scan_event` (`event_id` ASC) USING BTREE,
  INDEX `idx_exc_scan_rule` (`rule_code` ASC, `start_at` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '业务异常预警中心-规则扫描日志表' ROW_FORMAT = Dynamic;
