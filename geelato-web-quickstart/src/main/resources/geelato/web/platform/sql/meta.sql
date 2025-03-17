-- 常用mysql查询
-- 查询具有某字段的表
-- SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME like '%_schema' ORDER BY TABLE_NAME ASC

-- 创建表后更新关联元数据
-- @sql upgradeMetaAfterCreateTable
UPDATE platform_dev_table SET table_name = '$.tableName', synced = 1 WHERE id = '$.tableId';
UPDATE platform_dev_column SET synced = 1 WHERE del_status = 0 AND table_id = '$.tableId';
UPDATE platform_dev_table_check SET synced = 1 WHERE del_status = 0 AND table_id = '$.tableId';

-- 更新表后更新管理元数据
-- @sql upgradeMetaAfterUpdateTable
UPDATE platform_dev_table SET synced = 1 WHERE id = '$.tableId';
UPDATE platform_dev_column SET synced = 1 WHERE del_status = 0 AND table_id = '$.tableId';
UPDATE platform_dev_table_check SET synced = 1 WHERE del_status = 0 AND table_id = '$.tableId';

-- 链接模式schema更新后更新关联元数据
-- @sql upgradeMetaAfterUpdateSchema
UPDATE platform_dev_table SET table_schema = '$.dbSchema' WHERE connect_id = '$.id';
UPDATE platform_dev_view SET table_schema = '$.dbSchema' WHERE connect_id = '$.id';
UPDATE platform_dev_table_check SET table_schema = '$.dbSchema' WHERE connect_id = '$.id';
UPDATE platform_dev_column p1,platform_dev_table p2 SET p1.table_schema = '$.dbSchema' WHERE p1.table_id = p2.id AND connect_id = '$.id';

-- 链接模式view更新后更新关联元数据
-- @sql upgradeMetaAfterDelView
UPDATE platform_app_r_view SET enable_status = '$.enableStatus',del_status = '$.delStatus',delete_at = '$.deleteAt' WHERE view_id = '$.viewId' AND del_status = 0;
UPDATE platform_permission SET del_status = '$.delStatus',delete_at = '$.deleteAt' WHERE object = '$.viewName' and parent_object = '$.connectId' AND del_status = 0;

-- 创建表后更新关联元数据
-- @sql upgradeMetaAfterRenameTable
UPDATE platform_dev_column SET table_name = '$.newEntityName' WHERE table_id = '$.tableId';
UPDATE platform_dev_table_check SET table_name = '$.newEntityName' WHERE table_id = '$.tableId';
UPDATE platform_app_r_table SET table_name = '$.newEntityName' WHERE table_id = '$.tableId';
UPDATE platform_dev_table_foreign SET foreign_table = '$.newEntityName' WHERE foreign_table = '$.entityName';
UPDATE platform_dev_table_foreign SET main_table = '$.newEntityName' WHERE main_table = '$.entityName';
UPDATE platform_dev_view SET entity_name = '$.newEntityName' WHERE entity_name = '$.entityName' AND connect_id = '$.connectId';
UPDATE platform_app_r_view p1,platform_dev_view p2,platform_dev_table p3
    SET p1.table_name = '$.newEntityName'
WHERE p1.view_id = p2.id AND p2.connect_id = p3.connect_id AND p2.entity_name = p3.entity_name AND p3.id = '$.tableId'

-- 创建表后更新关联元数据
-- @sql upgradeMetaAfterDelTable
UPDATE platform_dev_column
    SET table_name = '$.newEntityName',enable_status = '$.enableStatus',del_status = '$.delStatus',delete_at = '$.deleteAt'
WHERE table_id = '$.tableId';
UPDATE platform_dev_table_check
    SET table_name = '$.newEntityName',enable_status = '$.enableStatus',del_status = '$.delStatus',delete_at = '$.deleteAt'
WHERE table_id = '$.tableId';
UPDATE platform_dev_view
    SET entity_name = '$.newEntityName',enable_status = '$.enableStatus',del_status = '$.delStatus',delete_at = '$.deleteAt'
WHERE entity_name = '$.entityName' AND connect_id = '$.connectId';
UPDATE platform_app_r_table
    SET table_name = '$.newEntityName',enable_status = '$.enableStatus',del_status = '$.delStatus',delete_at = '$.deleteAt'
WHERE table_id = '$.tableId';
UPDATE platform_dev_table_foreign
    SET main_table = '$.newEntityName',enable_status = '$.enableStatus',del_status = '$.delStatus',delete_at = '$.deleteAt'
WHERE main_table = '$.entityName';
UPDATE platform_dev_table_foreign
    SET foreign_table = '$.newEntityName',enable_status = '$.enableStatus',del_status = '$.delStatus',delete_at = '$.deleteAt'
WHERE foreign_table = '$.entityName';
UPDATE platform_permission
    SET del_status = '$.delStatus',delete_at = '$.deleteAt'
WHERE parent_object = '$.connectId' AND (object = '$.entityName' OR object LIKE '$.entityName:%') AND del_status = 0;
UPDATE platform_app_r_view p1,platform_dev_view p2,platform_dev_table p3
SET p1.table_name = '$.newEntityName',p1.enable_status = '$.enableStatus',p1.del_status = '$.delStatus',p1.delete_at = '$.deleteAt'
WHERE p1.view_id = p2.id AND p2.connect_id = p3.connect_id AND p2.entity_name = p3.entity_name AND p3.id = '$.tableId';

-- 删除字段后更新关联元数据
-- @sql upgradeMetaAfterDelColumn
UPDATE platform_dev_table_foreign
    SET del_status = '$.delStatus',delete_at = '$.deleteAt',enable_status = '$.enableStatus',description = CONCAT('$.remark',description)
WHERE del_status = 0 AND (main_table = '$.tableName' AND FIND_IN_SET('$.name',main_table_col)) OR (foreign_table = '$.tableName' AND FIND_IN_SET('$.name',foreign_table_col));
UPDATE platform_permission SET del_status = '$.delStatus',delete_at = '$.deleteAt' WHERE object = CONCAT_WS(':','$.tableName','$.name') and parent_object = '$.connectId' AND del_status = 0;

-- 字段更新后更新关联元数据
-- @sql upgradeMetaAfterUpdateColumn
UPDATE platform_dev_table_foreign SET main_table_col = '$.newName' WHERE del_status = 0 AND main_table = '$.tableName' AND FIND_IN_SET('$.name',main_table_col);
UPDATE platform_dev_table_foreign SET foreign_table_col = '$.newName' WHERE del_status = 0 AND foreign_table = '$.tableName' AND FIND_IN_SET('$.name',foreign_table_col);
UPDATE platform_permission SET object = CONCAT_WS(':','$.tableName','$.newName') WHERE object = CONCAT_WS(':','$.tableName','$.name') and parent_object = '$.connectId' AND del_status = 0;
