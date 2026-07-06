/*
 Navicat Premium Dump SQL

 Source Server         : 47.115.225.236(geelato.cn)
 Source Server Type    : MySQL
 Source Server Version : 80404 (8.4.4)
 Source Host           : 47.115.225.236:5310
 Source Schema         : geelato

 Target Server Type    : MySQL
 Target Server Version : 80404 (8.4.4)
 File Encoding         : 65001

 Date: 01/07/2026 12:15:55
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for platform_dict_item
-- ----------------------------
DROP TABLE IF EXISTS `platform_dict_item`;
CREATE TABLE `platform_dict_item`  (
  `id` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL,
  `app_id` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `pid` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '父级',
  `dict_id` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `item_code` varchar(1024) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `item_name` varchar(1024) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `item_remark` varchar(1024) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `seq_no` int NULL DEFAULT NULL,
  `enable_status` tinyint(1) NOT NULL DEFAULT 0,
  `dept_id` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '部门信息',
  `bu_id` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '企业信息',
  `tenant_code` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `del_status` int NOT NULL DEFAULT 0 COMMENT '逻辑删除状态，1：已删除、0：未删除',
  `update_at` datetime NOT NULL COMMENT '更新时间',
  `updater` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL,
  `updater_name` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '更新者名称',
  `create_at` datetime NOT NULL COMMENT '创建时间',
  `creator` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL,
  `creator_name` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '创建者名称',
  `delete_at` datetime NULL DEFAULT NULL COMMENT '删除时间',
  `item_color` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '字典项颜色',
  `item_tag` varchar(200) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '字典项标签',
  `item_name_en` varchar(1024) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '英文名称',
  `item_extra` varchar(1024) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '扩展字段1',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci COMMENT = '数据字典项' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of platform_dict_item
-- ----------------------------
INSERT INTO `platform_dict_item` (`id`, `app_id`, `pid`, `dict_id`, `item_code`, `item_name`, `item_remark`, `seq_no`, `enable_status`, `dept_id`, `bu_id`, `tenant_code`, `del_status`, `update_at`, `updater`, `updater_name`, `create_at`, `creator`, `creator_name`, `delete_at`, `item_color`, `item_tag`, `item_name_en`, `item_extra`)
VALUES
('9000000000000000201', NULL, NULL, '9000000000000000001', '0', '禁用', '示例字典项', 1, 1, NULL, NULL, 'geelato', 0, '2026-07-06 00:00:00', '3751618121485025347', 'gl_user', '2026-07-06 00:00:00', '3751618121485025347', 'gl_user', NULL, NULL, NULL, 'Disabled', NULL),
('9000000000000000202', NULL, NULL, '9000000000000000001', '1', '启用', '示例字典项', 2, 1, NULL, NULL, 'geelato', 0, '2026-07-06 00:00:00', '3751618121485025347', 'gl_user', '2026-07-06 00:00:00', '3751618121485025347', 'gl_user', NULL, NULL, NULL, 'Enabled', NULL),
('9000000000000000203', NULL, NULL, '9000000000000000001', '2', '未知', '示例字典项', 3, 1, NULL, NULL, 'geelato', 0, '2026-07-06 00:00:00', '3751618121485025347', 'gl_user', '2026-07-06 00:00:00', '3751618121485025347', 'gl_user', NULL, NULL, NULL, 'Unknown', NULL);

SET FOREIGN_KEY_CHECKS = 1;
