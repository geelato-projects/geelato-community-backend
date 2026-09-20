SET NAMES utf8mb4;

-- Table structure for platform_archive_policy
-- 归档策略表：声明"哪张表、按什么条件、归档到哪里、多快搬"，不包含执行逻辑。
-- 策略基于表（与实体解耦），通常由实体管理"开启归档"生成；默认停用，无启用策略绝不动数据。
CREATE TABLE IF NOT EXISTS `platform_archive_policy`  (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `name` varchar(128) NULL DEFAULT NULL COMMENT '名称',
  `code` varchar(64) NULL DEFAULT NULL COMMENT '编码（唯一，预置模板幂等识别）',
  `description` varchar(512) NULL DEFAULT NULL COMMENT '描述',
  `table_name` varchar(64) NOT NULL COMMENT '源表名',
  `source_entity_name` varchar(128) NULL DEFAULT NULL COMMENT '来源实体名（可选冗余，仅追溯回显）',
  `policy_type` varchar(16) NOT NULL DEFAULT 'DEFAULT' COMMENT '策略类型：DEFAULT（create_at 窗口）/ CUSTOM（任意 WHERE）',
  `retention_days` int NULL DEFAULT NULL COMMENT '保留天数（DEFAULT 策略）：create_at < 当前时间 - retention_days',
  `where_condition` varchar(2000) NULL DEFAULT NULL COMMENT '自定义条件（CUSTOM 策略）：任意 WHERE 片段，引擎原样拼入选数查询',
  `target_type` varchar(32) NOT NULL DEFAULT 'MYSQL' COMMENT '目标类型：MYSQL/MONGODB/ELASTICSEARCH（一期仅 MYSQL）',
  `connect_id` varchar(64) NULL DEFAULT NULL COMMENT '归档库连接标识（dev_db_connect 体系；空=主库自身）',
  `target_table_name` varchar(64) NULL DEFAULT NULL COMMENT '目标表名（空则推导：跨库同名；同库 {源表}_archive）',
  `executor` varchar(32) NOT NULL DEFAULT 'INLINE' COMMENT '执行器：一期固定 INLINE；外部执行器二期预留',
  `execution_mode` varchar(16) NOT NULL DEFAULT 'AUTO' COMMENT '执行模式：AUTO/SERVER/LOCAL_FILE/JDBC',
  `batch_size` int NOT NULL DEFAULT 200 COMMENT '每批行数（50~5000）',
  `batch_interval_ms` int NOT NULL DEFAULT 200 COMMENT '批间隔毫秒（限流）',
  `max_rows_per_run` bigint NOT NULL DEFAULT 1000000 COMMENT '单次运行行数上限（渐进式归档；0=不限）',
  `include_deleted` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否连同软删行归档',
  `enable_status` int NOT NULL DEFAULT 0 COMMENT '启用状态：0 停用（默认）、1 启用',
  `last_run_id` varchar(32) NULL DEFAULT NULL COMMENT '最近运行ID',
  `last_run_status` varchar(16) NULL DEFAULT NULL COMMENT '最近运行状态',
  `seq_no` bigint NOT NULL DEFAULT 0 COMMENT '次序（调度执行顺序）',
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
  UNIQUE INDEX `uk_code`(`code`) USING BTREE,
  INDEX `idx_table_name`(`table_name`) USING BTREE,
  INDEX `idx_enable_status`(`enable_status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '归档策略表' ROW_FORMAT = Dynamic;
