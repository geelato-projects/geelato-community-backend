/**
* 格式说明：每条语句之间必须用注释“@sql ”进行分割，@sql后跟sqlId
*/

-- 创建或修改视图
-- @sql mysql_createOrReplaceView
CREATE OR REPLACE VIEW $.viewName AS $.viewSql;

-- 查询视图
-- @sql mysql_queryViewsByName
SHOW CREATE VIEW $.viewName;

-- 删除视图，仅重命名视图
-- @sql mysql_replaceView
DROP VIEW IF EXISTS $.viewName;
CREATE OR REPLACE VIEW $.newViewName AS $.viewSql;

-- 查询所有表
-- @sql mysql_queryAllTables
SELECT * FROM information_schema.tables WHERE 1 = 1 AND TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE' $.condition ORDER BY TABLE_NAME ASC;

-- 查看表的所有字段
-- @sql mysql_queryColumnsByTableName
SELECT * FROM information_schema.columns WHERE TABLE_SCHEMA = DATABASE()
@if $.tableName!='' && $.tableName!=null
    AND TABLE_NAME = '$.tableName'
@/if
@if $.columnName!='' && $.columnName!=null
    AND COLUMN_NAME = '$.columnName'
@/if
ORDER BY ORDINAL_POSITION ASC;

-- 查询指定表的唯一索引（排除主键）
-- @sql mysql_queryUniqueIndexesByTableName
SHOW INDEXES FROM `$.tableName` WHERE Non_unique = 0 AND Key_name != 'PRIMARY';

-- 查询指定表的check约束
-- @sql mysql_queryChecksByTableName
SELECT t.* FROM
    (SELECT
        t1.CONSTRAINT_CATALOG,
        t1.CONSTRAINT_SCHEMA,
        t1.TABLE_SCHEMA,
        t1.TABLE_NAME,
        t1.CONSTRAINT_TYPE,
        t1.ENFORCED,
        t1.CONSTRAINT_NAME,
        t2.CHECK_CLAUSE
    FROM information_schema.TABLE_CONSTRAINTS t1
    JOIN information_schema.CHECK_CONSTRAINTS t2 ON t1.CONSTRAINT_NAME = t2.CONSTRAINT_NAME
    WHERE t1.TABLE_SCHEMA = DATABASE() AND t1.CONSTRAINT_TYPE = 'CHECK'
) t WHERE 1 = 1
@if $.tableName!='' && $.tableName!=null
    AND TABLE_NAME = '$.tableName'
@/if
@if $.constraintName!='' && $.constraintName!=null
    AND FIND_IN_SET(CONSTRAINT_NAME, '$.constraintName')
@/if

-- 创建表
-- @sql mysql_createTable
@for i in $.delCheckList
    ALTER TABLE $.delCheckList[i].tableName DROP CHECK $.delCheckList[i].constraintName;
@/for
CREATE TABLE IF NOT EXISTS $.tableName (
  @for i in $.addList
    `$.addList[i].name` $.addList[i].type
    @if !$.addList[i].nullable
      NOT NULL
    @/if
    @if $.addList[i].defaultValue!='' && $.addList[i].defaultValue!=null
      @if $.addList[i].dataType=='BIT'
        DEFAULT $.addList[i].defaultValue
      @/if
      @if $.addList[i].dataType!='BIT'
        DEFAULT '$.addList[i].defaultValue'
      @/if
    @/if
    @if $.addList[i].autoIncrement
      AUTO_INCREMENT
    @/if
    @if $.addList[i].comment!='' && $.addList[i].comment!=null
      COMMENT '$.addList[i].comment'
    @/if
    @if i<$.addList.length-1
      ,
    @/if
  @/for
  @if $.uniqueList.length>0 || ($.primaryKey!='' && $.primaryKey!=null)
    ,
  @/if
  @if $.primaryKey!='' && $.primaryKey!=null
  PRIMARY KEY ($.primaryKey) USING BTREE
  @/if
  @if $.uniqueList.length>0
  ,
  @/if
  @for i in $.uniqueList
    @if $.hasDelStatus
  UNIQUE INDEX `$.uniqueList[i].name`(`$.uniqueList[i].name`, `del_status`, `delete_at`) USING BTREE
    @/if
    @if !$.hasDelStatus
      UNIQUE INDEX `$.uniqueList[i].name`(`$.uniqueList[i].name`) USING BTREE
    @/if
    @if i<$.uniqueList.length-1
      ,
    @/if
  @/for
  @for i in $.checkList
    CONSTRAINT `$.checkList[i].code` CHECK ($.checkList[i].checkClause)
    @if i<$.checkList.length-1
     ,
    @/if
  @/for
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT '$.tableTitle';

-- 更新表
-- @sql mysql_upgradeTable
@if $.tableTitle!='' && $.tableTitle!=null
  ALTER TABLE $.tableName COMMENT = '$.tableTitle';
@/if
@for i in $.addList
  alter table $.tableName add $.addList[i].name $.addList[i].type
  @if !$.addList[i].nullable
    not null
  @/if
  @if $.addList[i].defaultValue!='' && $.addList[i].defaultValue!=null
    @if $.addList[i].dataType=='BIT'
      DEFAULT $.addList[i].defaultValue
    @/if
    @if $.addList[i].dataType!='BIT'
      DEFAULT '$.addList[i].defaultValue'
    @/if
  @/if
  @if $.addList[i].autoIncrement
     AUTO_INCREMENT
  @/if
  @if $.addList[i].comment!='' && $.addList[i].comment!=null
    COMMENT '$.addList[i].comment'
  @/if
  ;
@/for
@for i in $.changeList
alter table $.tableName CHANGE `$.changeList[i].befColName` `$.changeList[i].name` $.changeList[i].type
    @if !$.changeList[i].nullable
    not null
    @/if
    @if $.changeList[i].defaultValue!='' && $.changeList[i].defaultValue!=null
    @if $.changeList[i].dataType=='BIT'
    DEFAULT $.changeList[i].defaultValue
    @/if
    @if $.changeList[i].dataType!='BIT'
    DEFAULT '$.changeList[i].defaultValue'
    @/if
    @/if
    @if $.changeList[i].autoIncrement
    AUTO_INCREMENT
    @/if
    @if $.changeList[i].comment!='' && $.changeList[i].comment!=null
    COMMENT '$.changeList[i].comment'
    @/if
;
@/for
@for i in $.modifyList
  alter table $.tableName modify `$.modifyList[i].name` $.modifyList[i].type
  @if !$.modifyList[i].nullable
    not null
  @/if
  @if $.modifyList[i].defaultValue!='' && $.modifyList[i].defaultValue!=null
    @if $.modifyList[i].dataType=='BIT'
      DEFAULT $.modifyList[i].defaultValue
    @/if
    @if $.modifyList[i].dataType!='BIT'
      DEFAULT '$.modifyList[i].defaultValue'
    @/if
  @/if
  @if $.modifyList[i].autoIncrement
    AUTO_INCREMENT
  @/if
  @if $.modifyList[i].comment!='' && $.modifyList[i].comment!=null
    COMMENT '$.modifyList[i].comment'
  @/if
  ;
@/for
@for i in $.indexList
  alter table $.tableName drop index `$.indexList[i].keyName`;
@/for
@if $.primaryKey!='' && $.primaryKey!=null
 ALTER TABLE $.tableName DROP PRIMARY KEY,ADD PRIMARY KEY ($.primaryKey);
@/if
@for i in $.uniqueList
    @if $.hasDelStatus
ALTER TABLE $.tableName ADD UNIQUE INDEX `$.uniqueList[i].name`(`$.uniqueList[i].name`, `del_status`, `delete_at`) USING BTREE;
    @/if
    @if !$.hasDelStatus
ALTER TABLE $.tableName ADD UNIQUE INDEX `$.uniqueList[i].name`(`$.uniqueList[i].name`) USING BTREE;
    @/if
@/for
@for i in $.delCheckList
    ALTER TABLE $.delCheckList[i].tableName DROP CHECK $.delCheckList[i].constraintName;
@/for
@for i in $.checkList
  ALTER TABLE $.checkList[i].tableName ADD CONSTRAINT `$.checkList[i].code` CHECK ($.checkList[i].checkClause);
@/for

-- 模型变更，表名变更，表重命名
-- @sql mysql_renameTable
ALTER TABLE $.entityName COMMENT = '$.newComment';
RENAME TABLE $.entityName TO $.newEntityName;

-- 字段变更，删除字段时,更新时
-- @sql mysql_renameColumn
alter table $.tableName CHANGE COLUMN `$.name` `$.newName` $.type
@if !$.nullable
    not null
@/if
@if $.defaultValue!='' && $.defaultValue!=null
    @if $.dataType=='BIT'
        DEFAULT $.defaultValue
    @/if
    @if $.dataType!='BIT'
        DEFAULT '$.defaultValue'
    @/if
@/if
@if $.autoIncrement
    AUTO_INCREMENT
@/if
@if $.comment!='' && $.comment!=null
    COMMENT '$.comment'
@/if
;

-- 获取数据库表外键信息
-- @sql mysql_queryForeignsByTableName
SELECT
  t.CONSTRAINT_CATALOG,
  t.CONSTRAINT_SCHEMA,
  t.CONSTRAINT_NAME,
  t.CONSTRAINT_TYPE,
  t.ENFORCED,
  t.TABLE_SCHEMA,
  t.TABLE_NAME,
  k.COLUMN_NAME,
  k.REFERENCED_TABLE_SCHEMA,
  k.REFERENCED_TABLE_NAME,
  k.REFERENCED_COLUMN_NAME,
  r.UPDATE_RULE,
  r.DELETE_RULE
FROM information_schema.TABLE_CONSTRAINTS t
LEFT JOIN information_schema.KEY_COLUMN_USAGE k ON t.TABLE_SCHEMA = k.TABLE_SCHEMA AND t.CONSTRAINT_NAME = k.CONSTRAINT_NAME
LEFT JOIN information_schema.REFERENTIAL_CONSTRAINTS r ON k.TABLE_SCHEMA = r.CONSTRAINT_SCHEMA AND k.CONSTRAINT_NAME = r.CONSTRAINT_NAME
WHERE 1 = 1
  AND t.CONSTRAINT_TYPE = 'FOREIGN KEY'
  AND t.TABLE_SCHEMA = DATABASE()
  AND t.TABLE_NAME = '$.tableName';
