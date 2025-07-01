-- 创建或修改视图
-- @sql sqlserver_createOrReplaceView
IF OBJECT_ID('$.viewName', 'V') IS NOT NULL
    DROP VIEW $.viewName;
GO
CREATE OR REPLACE VIEW $.viewName AS $.viewSql;

-- 查询视图
-- @sql sqlserver_queryViewsByName
SELECT definition FROM sys.sql_modules WHERE object_id = OBJECT_ID('$.viewName');

-- 删除视图，仅重命名视图
-- @sql sqlserver_replaceView
EXEC sp_rename '$.viewName', '$.newViewName';

-- 查询所有表
-- @sql sqlserver_queryAllTables
SELECT * FROM information_schema.tables WHERE TABLE_CATALOG = DB_NAME() AND TABLE_TYPE = 'BASE TABLE' $.condition ORDER BY TABLE_NAME ASC;

-- 查看表的所有字段
-- @sql sqlserver_queryColumnsByTableName
SELECT * FROM information_schema.columns WHERE TABLE_CATALOG = DB_NAME()
@if $.tableName!='' && $.tableName!=null
    AND TABLE_NAME = '$.tableName'
@/if
@if $.columnName!='' && $.columnName!=null
    AND COLUMN_NAME = '$.columnName'
@/if
ORDER BY ORDINAL_POSITION ASC;

-- 查询指定表的唯一索引（排除主键）
-- @sql sqlserver_queryUniqueIndexesByTableName
SELECT
    i.name AS IndexName,
    t.name AS TableName,
    c.name AS ColumnName
FROM sys.indexes i
JOIN sys.tables t ON i.object_id = t.object_id
JOIN sys.index_columns ic ON i.object_id = ic.object_id AND i.index_id = ic.index_id
JOIN sys.columns c ON ic.object_id = c.object_id AND ic.column_id = c.column_id
WHERE t.name = '$.tableName' AND i.is_unique = 1 AND i.is_primary_key = 0
ORDER BY i.name, ic.key_ordinal;

-- 查询指定表的check约束
-- @sql sqlserver_queryChecksByTableName
SELECT t.* FROM
    (SELECT
        t1.CONSTRAINT_CATALOG,
        t1.CONSTRAINT_SCHEMA,
        t1.TABLE_SCHEMA,
        t1.TABLE_NAME,
        t1.CONSTRAINT_TYPE,
        'NO' AS ENFORCED,
        t1.CONSTRAINT_NAME,
        t2.CHECK_CLAUSE
    FROM information_schema.TABLE_CONSTRAINTS t1
    JOIN information_schema.CHECK_CONSTRAINTS t2 ON t1.CONSTRAINT_NAME = t2.CONSTRAINT_NAME
    WHERE t1.TABLE_CATALOG = DB_NAME() AND t1.CONSTRAINT_TYPE = 'CHECK'
) t WHERE 1 = 1
@if $.tableName!='' && $.tableName!=null
    AND TABLE_NAME = '$.tableName'
@/if
@if $.constraintName!='' && $.constraintName!=null
    AND CONSTRAINT_NAME IN (SELECT value FROM STRING_SPLIT('$.constraintName', ','))
@/if

-- 创建表
-- @sql sqlserver_createTable
@for i in $.delCheckList
    ALTER TABLE $.delCheckList[i].tableName DROP CONSTRAINT $.delCheckList[i].constraintName;
@/for
IF OBJECT_ID('$.tableName', 'U') IS NULL
BEGIN
CREATE TABLE $.tableName (
    @for i in $.addList
        [$.addList[i].name] $.addList[i].type
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
            IDENTITY(1,1)
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
        PRIMARY KEY ($.primaryKey)
    @/if
    @if $.uniqueList.length>0
    ,
    @/if
    @for i in $.uniqueList
        @if $.hasDelStatus
            CONSTRAINT [$.uniqueList[i].name] UNIQUE ([$.uniqueList[i].name], [del_status], [delete_at])
        @/if
        @if !$.hasDelStatus
            CONSTRAINT [$.uniqueList[i].name] UNIQUE ([$.uniqueList[i].name])
        @/if
        @if i<$.uniqueList.length-1
        ,
        @/if
    @/for
    @for i in $.checkList
        CONSTRAINT [$.checkList[i].code] CHECK ($.checkList[i].checkClause)
        @if i<$.checkList.length-1
        ,
        @/if
    @/for
);
END

-- 更新表
-- @sql sqlserver_upgradeTable
@if $.tableTitle!='' && $.tableTitle!=null
  EXEC sp_addextendedproperty
    'MS_Description', '$.tableTitle',
    'SCHEMA', 'dbo',
    'TABLE', '$.tableName';
@/if
@for i in $.addList
ALTER TABLE $.tableName ADD $.addList[i].name $.addList[i].type
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
  @if $.addList[i].comment!='' && $.addList[i].comment!=null
    -- $.addList[i].comment
  @/if
;
@/for
@for i in $.changeList
ALTER TABLE $.tableName ALTER COLUMN $.changeList[i].befColName $.changeList[i].type
    @if !$.changeList[i].nullable
      NOT NULL
    @/if
    @if $.changeList[i].defaultValue!='' && $.changeList[i].defaultValue!=null
      @if $.changeList[i].dataType=='BIT'
        DEFAULT $.changeList[i].defaultValue
      @/if
      @if $.changeList[i].dataType!='BIT'
        DEFAULT '$.changeList[i].defaultValue'
      @/if
    @/if
;
EXEC sp_rename '$.tableName.$.changeList[i].befColName', '$.changeList[i].name', 'COLUMN';
@/for
@for i in $.modifyList
ALTER TABLE $.tableName ALTER COLUMN $.modifyList[i].name $.modifyList[i].type
    @if !$.modifyList[i].nullable
      NOT NULL
    @/if
    @if $.modifyList[i].defaultValue!='' && $.modifyList[i].defaultValue!=null
      @if $.modifyList[i].dataType=='BIT'
        DEFAULT $.modifyList[i].defaultValue
      @/if
      @if $.modifyList[i].dataType!='BIT'
        DEFAULT '$.modifyList[i].defaultValue'
      @/if
    @/if
;
@/for
@for i in $.indexList
    DROP INDEX [$.indexList[i].keyName] ON $.tableName;
@/for
@if $.primaryKey!='' && $.primaryKey!=null
    ALTER TABLE $.tableName DROP CONSTRAINT [PK_$.tableName];
    ALTER TABLE $.tableName ADD CONSTRAINT [PK_$.tableName] PRIMARY KEY ($.primaryKey);
@/if
@for i in $.uniqueList
    @if $.hasDelStatus
        ALTER TABLE $.tableName ADD CONSTRAINT [UQ_$.uniqueList[i].name] UNIQUE ([$.uniqueList[i].name], [del_status], [delete_at]);
    @/if
    @if !$.hasDelStatus
        ALTER TABLE $.tableName ADD CONSTRAINT [UQ_$.uniqueList[i].name] UNIQUE ([$.uniqueList[i].name]);
    @/if
@/for
@for i in $.delCheckList
    ALTER TABLE $.delCheckList[i].tableName DROP CONSTRAINT $.delCheckList[i].constraintName;
@/for
@for i in $.checkList
    ALTER TABLE $.checkList[i].tableName ADD CONSTRAINT [$.checkList[i].code] CHECK ($.checkList[i].checkClause);
@/for

-- 模型变更，表名变更，表重命名
-- @sql sqlserver_renameTable
EXEC sp_rename '$.entityName', '$.newEntityName';
EXEC sp_addextendedproperty
    'MS_Description', '$.newComment',
    'SCHEMA', 'dbo',
    'TABLE',  '$.entityName';

-- 字段变更，删除字段时
-- @sql sqlserver_renameColumn
-- 修改字段类型和是否可为空
ALTER TABLE $.tableName
ALTER COLUMN $.name $.type
@if !$.nullable
    NOT NULL
@/if;
EXEC sp_rename '$.tableName.$.name', '$.newName', 'COLUMN';
@if $.defaultValue!='' && $.defaultValue!=null
ALTER TABLE $.tableName
    ADD CONSTRAINT DF_$.tableName_$.newName DEFAULT '$.defaultValue' FOR $.newName;
@/if
@if $.comment!='' && $.comment!=null
    EXEC sp_addextendedproperty
        'MS_Description', '$.comment',
        'SCHEMA', 'dbo',
        'TABLE', '$.tableName',
        'COLUMN', '$.newName';
@/if

-- 获取数据库表外键信息
-- @sql sqlserver_queryForeignsByTableName
SELECT
    t.CONSTRAINT_CATALOG,
    t.CONSTRAINT_SCHEMA,
    t.CONSTRAINT_NAME,
    t.CONSTRAINT_TYPE,
    NULL AS ENFORCED,
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
  AND t.TABLE_SCHEMA = SCHEMA_NAME()
  AND t.TABLE_NAME = '$.tableName';
