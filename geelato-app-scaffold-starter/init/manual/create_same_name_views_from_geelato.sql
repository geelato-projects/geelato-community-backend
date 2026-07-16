/*
  用途：
  在目标库 A 中创建与 geelato 库同名的只读视图，便于直接复用平台基础数据。

  前提：
  1. A 库与 geelato 库必须位于同一个 MySQL 实例中，才能通过 `geelato.table_name` 直接引用。
  2. A 库中不能已存在同名物理表；如果已有同名表，请不要直接执行本脚本。
  3. 建议先执行：USE your_db_a;

  说明：
  - 本文件放在 init/manual 下，避免被 starter 的 auto-init-tables 自动扫描执行。
  - 如需切换源库名，可将下方所有 `geelato` 替换为实际源 schema。
*/

SET NAMES utf8mb4;

DROP VIEW IF EXISTS `platform_dev_column`;
CREATE VIEW `platform_dev_column` AS
SELECT * FROM `geelato`.`platform_dev_column`;

DROP VIEW IF EXISTS `platform_dev_db_connect`;
CREATE VIEW `platform_dev_db_connect` AS
SELECT * FROM `geelato`.`platform_dev_db_connect`;

DROP VIEW IF EXISTS `platform_dev_table`;
CREATE VIEW `platform_dev_table` AS
SELECT * FROM `geelato`.`platform_dev_table`;

DROP VIEW IF EXISTS `platform_dev_table_check`;
CREATE VIEW `platform_dev_table_check` AS
SELECT * FROM `geelato`.`platform_dev_table_check`;

DROP VIEW IF EXISTS `platform_dev_table_foreign`;
CREATE VIEW `platform_dev_table_foreign` AS
SELECT * FROM `geelato`.`platform_dev_table_foreign`;

DROP VIEW IF EXISTS `platform_dev_view`;
CREATE VIEW `platform_dev_view` AS
SELECT * FROM `geelato`.`platform_dev_view`;

DROP VIEW IF EXISTS `platform_dict`;
CREATE VIEW `platform_dict` AS
SELECT * FROM `geelato`.`platform_dict`;

DROP VIEW IF EXISTS `platform_dict_item`;
CREATE VIEW `platform_dict_item` AS
SELECT * FROM `geelato`.`platform_dict_item`;

DROP VIEW IF EXISTS `platform_org`;
CREATE VIEW `platform_org` AS
SELECT * FROM `geelato`.`platform_org`;

DROP VIEW IF EXISTS `platform_org_r_user`;
CREATE VIEW `platform_org_r_user` AS
SELECT * FROM `geelato`.`platform_org_r_user`;

DROP VIEW IF EXISTS `platform_permission`;
CREATE VIEW `platform_permission` AS
SELECT * FROM `geelato`.`platform_permission`;

DROP VIEW IF EXISTS `platform_role`;
CREATE VIEW `platform_role` AS
SELECT * FROM `geelato`.`platform_role`;

DROP VIEW IF EXISTS `platform_role_r_permission`;
CREATE VIEW `platform_role_r_permission` AS
SELECT * FROM `geelato`.`platform_role_r_permission`;

DROP VIEW IF EXISTS `platform_role_r_user`;
CREATE VIEW `platform_role_r_user` AS
SELECT * FROM `geelato`.`platform_role_r_user`;

DROP VIEW IF EXISTS `platform_sys_config`;
CREATE VIEW `platform_sys_config` AS
SELECT * FROM `geelato`.`platform_sys_config`;

DROP VIEW IF EXISTS `platform_tenant`;
CREATE VIEW `platform_tenant` AS
SELECT * FROM `geelato`.`platform_tenant`;

DROP VIEW IF EXISTS `platform_user`;
CREATE VIEW `platform_user` AS
SELECT * FROM `geelato`.`platform_user`;

DROP VIEW IF EXISTS `platform_user_r_permission`;
CREATE VIEW `platform_user_r_permission` AS
SELECT * FROM `geelato`.`platform_user_r_permission`;
