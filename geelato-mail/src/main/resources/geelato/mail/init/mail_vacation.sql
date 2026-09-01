-- geelato-mail mail_vacation 表（提取自 fms db/migration/V130__create_mail_p3_settings_tables.sql，catalog 逻辑分组 mail，表名不加库前缀）
-- 幂等：由 MailSchemaInitializer 不存在该表时执行（落位跟随 ORM 路由，缺省主库）

CREATE TABLE IF NOT EXISTS `mail_vacation` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键（雪花ID）',
  `user_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '所属用户ID（数据隔离）',
  `enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否启用',
  `subject` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '自动回复主题',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '自动回复正文',
  `only_contacts` tinyint(1) NOT NULL DEFAULT 0 COMMENT '仅回复联系人',
  `start_time` datetime NULL DEFAULT NULL COMMENT '假期开始时间（NULL=立即生效）',
  `end_time` datetime NULL DEFAULT NULL COMMENT '假期结束时间（NULL=不限）',
  `last_sent_at` datetime NULL DEFAULT NULL COMMENT '最近一次自动回复时间（引擎回写，预留）',
  `seq_no` int NULL DEFAULT 0 COMMENT '排序',
  `tenant_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '租户编码',
  `dept_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '部门',
  `bu_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '单位',
  `del_status` int NOT NULL DEFAULT 0 COMMENT '逻辑删除状态，1：已删除、0：未删除',
  `update_at` datetime NOT NULL COMMENT '更新时间',
  `updater` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '更新者',
  `updater_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新者名称',
  `create_at` datetime NOT NULL COMMENT '创建时间',
  `creator` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '创建者',
  `creator_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者名称',
  `delete_at` datetime NULL DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_status` (`user_id` ASC, `del_status` ASC) USING BTREE,
  INDEX `idx_tenant_status` (`tenant_code` ASC, `del_status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '邮件假期自动回复配置表' ROW_FORMAT = Dynamic;
