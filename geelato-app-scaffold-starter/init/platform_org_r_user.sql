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

 Date: 01/07/2026 12:16:14
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for platform_org_r_user
-- ----------------------------
DROP TABLE IF EXISTS `platform_org_r_user`;
CREATE TABLE `platform_org_r_user`  (
  `id` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '主键',
  `user_id` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `org_id` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `default_org` int NULL DEFAULT 0,
  `user_name` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `org_name` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `post` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '职务',
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
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci COMMENT = '组织用户关系表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of platform_org_r_user
-- ----------------------------
INSERT INTO `platform_org_r_user` (`id`, `user_id`, `org_id`, `default_org`, `user_name`, `org_name`, `post`, `dept_id`, `bu_id`, `tenant_code`, `del_status`, `update_at`, `updater`, `updater_name`, `create_at`, `creator`, `creator_name`, `delete_at`)
VALUES ('9000000000000000301', '3751618121485025347', '9000000000000000101', 1, 'gl_user', '示例组织', NULL, NULL, NULL, 'geelato', 0, '2026-07-06 00:00:00', '3751618121485025347', 'gl_user', '2026-07-06 00:00:00', '3751618121485025347', 'gl_user', NULL);

SET FOREIGN_KEY_CHECKS = 1;
