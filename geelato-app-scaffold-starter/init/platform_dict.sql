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

 Date: 01/07/2026 12:15:39
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for platform_dict
-- ----------------------------
DROP TABLE IF EXISTS `platform_dict`;
CREATE TABLE `platform_dict`  (
  `id` bigint NOT NULL COMMENT '序号',
  `app_id` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '所属应用',
  `dict_code` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '字典编码',
  `dict_name` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '字典名称',
  `dict_remark` varchar(1024) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '字典备注',
  `enable_status` int NULL DEFAULT 0 COMMENT '启用状态',
  `dept_id` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '部门信息',
  `bu_id` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '企业信息',
  `tenant_code` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '租户编码',
  `del_status` int NULL DEFAULT 0 COMMENT '删除状态',
  `update_at` datetime NOT NULL COMMENT '更新时间',
  `updater` bigint NOT NULL COMMENT '更新者',
  `updater_name` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '更新者名称',
  `create_at` datetime NOT NULL COMMENT '创建时间',
  `creator` bigint NOT NULL COMMENT '创建者',
  `creator_name` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '创建者名称',
  `delete_at` datetime NULL DEFAULT NULL COMMENT '删除时间',
  `seq_no` int UNSIGNED NULL DEFAULT 0 COMMENT '次序',
  `dict_color` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '字典颜色',
  `dict_name_en` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '字典英文名称',
  `extra_content` text CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL COMMENT '扩展内容',
  `extra_name` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '扩展名称',
  `extra_value_type` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '扩展值类型',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `dic_code`(`dict_code` ASC, `del_status` ASC, `delete_at` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci COMMENT = '数据字典' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of platform_dict
-- ----------------------------
INSERT INTO `platform_dict` (`id`, `app_id`, `dict_code`, `dict_name`, `dict_remark`, `enable_status`, `dept_id`, `bu_id`, `tenant_code`, `del_status`, `update_at`, `updater`, `updater_name`, `create_at`, `creator`, `creator_name`, `delete_at`, `seq_no`, `dict_color`, `dict_name_en`, `extra_content`, `extra_name`, `extra_value_type`)
VALUES (9000000000000000001, NULL, 'demo_status', '示例状态', '脚手架初始化示例字典：用于演示字典维护与按 code 获取', 1, NULL, NULL, 'geelato', 0, '2026-07-06 00:00:00', 3751618121485025347, 'gl_user', '2026-07-06 00:00:00', 3751618121485025347, 'gl_user', NULL, 1, NULL, 'Demo Status', NULL, NULL, NULL);

SET FOREIGN_KEY_CHECKS = 1;
