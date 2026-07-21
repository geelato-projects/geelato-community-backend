/*
 Navicat Premium Dump SQL

 Source Server         : 47.115.225.236(root)
 Source Server Type    : MySQL
 Source Server Version : 80404 (8.4.4)
 Source Host           : 47.115.225.236:5310
 Source Schema         : geelato

 Target Server Type    : MySQL
 Target Server Version : 80404 (8.4.4)
 File Encoding         : 65001

 Date: 21/07/2026
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for platform_user_r_delegate
-- 用户委托关系表
-- 记录用户之间的委托/助理关系，委托人(user_id)可配置代理人(delegate_user_id)代为处理事务。
-- 同一委托人为同一代理人配置多个业务范围时，存为多行（每行业务范围 scope 单值）。
-- 业务唯一性：user_id + delegate_user_id + scope 三者组合不重复（由应用层校验）。
-- ----------------------------
DROP TABLE IF EXISTS `platform_user_r_delegate`;
CREATE TABLE `platform_user_r_delegate`  (
  `id` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '主键',
  `user_id` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '委托人ID，发起委托的用户',
  `user_name` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '委托人名称（冗余回填）',
  `delegate_user_id` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '代理人ID，被委托的用户',
  `delegate_user_name` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '代理人名称（冗余回填）',
  `delegate_user_en_name` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '代理人英文名（冗余回填）',
  `relation_type` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT 'delegate' COMMENT '关系类型，默认 delegate，预留扩展',
  `scope` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '业务范围单值，如 periodic_report；空表示全部业务',
  `enable_status` int NULL DEFAULT 1 COMMENT '启用状态，1：启用、0：未启用',
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
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_delegate_scope`(`user_id`, `delegate_user_id`, `scope`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci COMMENT = '用户委托关系表' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
