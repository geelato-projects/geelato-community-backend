/**
* 格式说明：每条语句之间必须用注释“@sql ”进行分割，@sql后跟sqlId
*/

-- 创建表
-- @sql createOneTable
CREATE TABLE IF NOT EXISTS $.tableName (
  @for i in $.addList
    `$.addList[i].name` $.addList[i].type
    @if !$.addList[i].nullable
      not null
    @/if
    @if $.addList[i].defaultValue!='' && $.addList[i].defaultValue!=null
      DEFAULT $.addList[i].defaultValue
    @/if
    @if i<$.addList.length-1
      ,
    @/if
  @/for
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
@for i in $.uniqueList
  alter table $.tableName add unique key(`$.uniqueList[i].name`);
@/for


-- 创建表，id字段自增
-- @sql createOneTableAutoIncrement
CREATE TABLE IF NOT EXISTS $.tableName (id bigint(20) NOT NULL AUTO_INCREMENT,PRIMARY KEY (id)) ENGINE=InnoDB
DEFAULT CHARSET=utf8;
ALTER TABLE $.tableName
@for i in $.addList
  ADD COLUMN $.addList[i].name $.addList[i].type
  @if $.addList[i].nullable
    not null
  @/if
  @if $.addList[i].defaultValue!='' && $.addList[i].defaultValue!=null
    DEFAULT $.addList[i].defaultValue
  @/if
  @if i<$.addList.length-1
    ,
  @/if
@/for
@for i in $.uniqueList
  alter table $.tableName add unique key(`$.uniqueList[i].name`);
@/for

-- 删除表
-- @sql dropOneTable
DROP TABLE IF EXISTS $.tableName;

-- 查看列信息
-- @sql showColumns
show columns from $.tableName;


-- 从数据库字典中同步表信息到平台的元数据表中
-- @sql syncTableSchemaToConfig
INSERT INTO geelato.platform_dev_table (
  TABLE_SCHEMA,
  TABLE_NAME,
  TABLE_TYPE,
  TABLE_COMMENT,
  title,
  linked,
  activity,
  create_date,
  update_date,
  creator,
  updater,
  description
) SELECT
    TABLE_SCHEMA,
    TABLE_NAME,
    TABLE_TYPE,
    TABLE_COMMENT,
    TABLE_COMMENT AS title,
    1 AS linked,
    1 AS activity,
    NOW() AS create_date,
    NOW() AS update_date,
    1 AS creator,
    1 AS updater,
    '' AS description
  FROM
    information_schema. TABLES
  WHERE
    TABLE_SCHEMA = 'geelato'
    AND TABLE_NAME NOT IN (
      SELECT
        table_name
      FROM
        xpm.platform_dev_table
    )

-- 在数据库中增加字段 INT (1)
-- @sql addColumn
ALTER TABLE $.tableName ADD COLUMN $.name $.type
@if $.nullable
  not null
@/if
@if $.defaultValue!=null
  DEFAULT $.defaultValue;
@/if


-- 在数据库中创建修改视图
-- @sql createOneView
CREATE OR REPLACE VIEW $.viewName
    AS
    $.viewSql
;

