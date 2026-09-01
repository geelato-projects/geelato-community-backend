-- geelato-mail mail_auto_reply_log 表（提取自 fms db/migration/V132__create_mail_auto_reply_log.sql，catalog 逻辑分组 mail，表名不加库前缀）
-- 幂等：由 MailSchemaInitializer 不存在该表时执行（落位跟随 ORM 路由，缺省主库）

CREATE TABLE IF NOT EXISTS `mail_auto_reply_log` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键（雪花ID）',
  `user_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '所属用户ID（数据隔离）',
  `sender_email` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '发件人地址（小写归一化）',
  `reply_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '回复类型（filter=过滤器autoReply动作；vacation=假期自动回复）',
  `ref_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '关联ID（filter=mail_filter.id；vacation=空串）',
  `mail_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '触发回复的收件邮件ID（mail_message.id）',
  `reply_subject` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '回复主题',
  `sent_at` datetime NOT NULL COMMENT '发送时间',
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
  INDEX `idx_user_sender` (`user_id` ASC, `sender_email` ASC, `reply_type` ASC, `ref_id` ASC, `del_status` ASC, `sent_at` DESC) USING BTREE,
  INDEX `idx_tenant_status` (`tenant_code` ASC, `del_status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '邮件自动回复发送台账表' ROW_FORMAT = Dynamic;
