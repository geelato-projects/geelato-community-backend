/*
 Navicat Premium Dump SQL

 Source Server         : 47.121.135.61_5050
 Source Server Type    : MySQL
 Source Server Version : 80035 (8.0.35)
 Source Host           : 47.121.135.61:5050
 Source Schema         : geelato

 Target Server Type    : MySQL
 Target Server Version : 80035 (8.0.35)
 File Encoding         : 65001

 Date: 15/07/2026 10:55:47
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for platform_tenant
-- ----------------------------
DROP TABLE IF EXISTS `platform_tenant`;
CREATE TABLE `platform_tenant`  (
  `id` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '主键',
  `code` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '企业的编码即租户编码',
  `corp_id` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT 'corpId',
  `company_name` varchar(125) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '企业名称',
  `company_domain` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '企业的自定义域名，例如：www.xxx.com',
  `main_email_pwd` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '主邮箱密码',
  `main_email` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '主邮箱',
  `corp_token` varchar(1024) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT 'corpToken',
  `dept_id` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '部门信息',
  `bu_id` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '企业信息',
  `tenant_code` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '租户编码',
  `del_status` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除状态，1：已删除、0：未删除',
  `update_at` datetime NOT NULL COMMENT '更新时间',
  `updater` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '更新者',
  `updater_name` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '更新者名称',
  `create_at` datetime NOT NULL COMMENT '创建时间',
  `creator` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '创建者',
  `creator_name` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '创建者名称',
  `delete_at` datetime NULL DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `code`(`code` ASC, `del_status` ASC, `delete_at` ASC) USING BTREE,
  UNIQUE INDEX `company_name`(`company_name` ASC, `del_status` ASC, `delete_at` ASC) USING BTREE,
  UNIQUE INDEX `corp_id`(`corp_id` ASC, `del_status` ASC, `delete_at` ASC) USING BTREE,
  UNIQUE INDEX `company_domain`(`company_domain` ASC, `del_status` ASC, `delete_at` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci COMMENT = '租户管理' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
