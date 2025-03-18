-- 创建或修改视图
-- @sql oracle_createOrReplaceView
CREATE OR REPLACE VIEW $.viewName AS $.viewSql;

-- 查询视图
-- @sql oracle_queryViewsByName
SELECT TEXT FROM user_views WHERE view_name = '$.viewName';

-- 删除视图，仅重命名视图
-- @sql oracle_replaceView
DROP VIEW $.viewName;
CREATE OR REPLACE VIEW $.newViewName AS $.viewSql;

-- 查询所有表
-- @sql oracle_queryAllTables
SELECT * FROM all_tables WHERE OWNER = USER $.condition ORDER BY TABLE_NAME ASC;

-- 查看表的所有字段
-- @sql oracle_queryColumnsByTableName
SELECT * FROM all_tab_columns WHERE OWNER = USER
@if $.tableName!='' && $.tableName!=null
    AND TABLE_NAME = '$.tableName'
@/if
@if $.columnName!='' && $.columnName!=null
    AND COLUMN_NAME = '$.columnName'
@/if
ORDER BY COLUMN_ID ASC;

-- 查询指定表的唯一索引（排除主键）
-- @sql oracle_queryUniqueIndexesByTableName
SELECT
    i.index_name AS IndexName,
    i.table_name AS TableName,
    c.column_name AS ColumnName
FROM all_indexes i
JOIN all_ind_columns c ON i.index_name = c.index_name AND i.table_name = c.table_name
WHERE i.table_name = '$.tableName' AND i.uniqueness = 'UNIQUE'
AND i.index_name NOT IN (SELECT constraint_name FROM all_constraints WHERE constraint_type = 'P' AND table_name = '$.tableName')
ORDER BY i.index_name, c.column_position;

-- 查询指定表的check约束
-- @sql oracle_queryChecksByTableName
SELECT t.* FROM
    (SELECT
        t1.OWNER AS CONSTRAINT_SCHEMA,
        t1.TABLE_NAME,
        'CHECK' AS CONSTRAINT_TYPE,
        t1.CONSTRAINT_NAME,
        t1.SEARCH_CONDITION AS CHECK_CLAUSE
    FROM all_constraints t1
    WHERE t1.OWNER = USER AND t1.CONSTRAINT_TYPE = 'C'
     ) t WHERE 1 = 1
@if $.tableName!='' && $.tableName!=null
    AND TABLE_NAME = '$.tableName'
@/if
@if $.constraintName!='' && $.constraintName!=null
    AND CONSTRAINT_NAME IN (
        SELECT REGEXP_SUBSTR('$.constraintName', '[^,]+', 1, LEVEL)
        FROM DUAL
        CONNECT BY REGEXP_SUBSTR('$.constraintName', '[^,]+', 1, LEVEL) IS NOT NULL
    )
@/if

-- 创建表
-- @sql oracle_createTable
@for i in $.delCheckList
    ALTER TABLE $.delCheckList[i].tableName DROP CONSTRAINT $.delCheckList[i].constraintName;
@/for
CREATE TABLE $.tableName (
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
        @if $.addList[i].comment!='' && $.addList[i].comment!=null
            -- $.addList[i].comment
        @/if
        @if i<$.addList.length-1
        ,
        @/if
    @/for
     @if $.primaryKey!='' && $.primaryKey!=null
    ,
     CONSTRAINT `pk_$.tableName` PRIMARY KEY ($.primaryKey)
    @/if
    @if $.uniqueList.length>0
    ,
    @/if
     @for i in $.uniqueList
     @if $.hasDelStatus
     CONSTRAINT `$.uniqueList[i].name` UNIQUE (`$.uniqueList[i].name`, `del_status`, `delete_at`)
    @/if
    @if !$.hasDelStatus
    CONSTRAINT `$.uniqueList[i].name` UNIQUE (`$.uniqueList[i].name`)
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
);

-- 更新表
-- @sql oracle_upgradeTable
@if $.tableTitle!='' && $.tableTitle!=null
  COMMENT ON TABLE $.tableName IS '$.tableTitle';
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
ALTER TABLE $.tableName MODIFY $.changeList[i].befColName $.changeList[i].type
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
ALTER TABLE $.tableName RENAME COLUMN $.changeList[i].befColName TO $.changeList[i].name;
@/for
@for i in $.modifyList
ALTER TABLE $.tableName MODIFY $.modifyList[i].name $.modifyList[i].type
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
    DROP INDEX `$.indexList[i].keyName`;
@/for
@if $.primaryKey!='' && $.primaryKey!=null
    ALTER TABLE $.tableName DROP CONSTRAINT [PK_$.tableName];
    ALTER TABLE $.tableName ADD CONSTRAINT [PK_$.tableName] PRIMARY KEY ($.primaryKey);
@/if
@for i in $.uniqueList
    @if $.hasDelStatus
        ALTER TABLE $.tableName ADD CONSTRAINT `UQ_$.uniqueList[i].name` UNIQUE (`$.uniqueList[i].name`, `del_status`, `delete_at`);
    @/if
    @if !$.hasDelStatus
        ALTER TABLE $.tableName ADD CONSTRAINT `UQ_$.uniqueList[i].name` UNIQUE (`$.uniqueList[i].name`);
    @/if
@/for
@for i in $.delCheckList
    ALTER TABLE $.delCheckList[i].tableName DROP CONSTRAINT $.delCheckList[i].constraintName;
@/for
@for i in $.checkList
    ALTER TABLE $.checkList[i].tableName ADD CONSTRAINT `$.checkList[i].code` CHECK ($.checkList[i].checkClause);
@/for

-- 模型变更，表名变更，表重命名
-- @sql oracle_renameTable
ALTER TABLE $.entityName RENAME TO $.newEntityName;
COMMENT ON TABLE $.entityName IS '$.newComment';

-- 字段变更，删除字段时
-- @sql oracle_renameColumn
ALTER TABLE $.tableName MODIFY $.newName $.type
@if !$.nullable
    NOT NULL
@/if;
ALTER TABLE $.tableName RENAME COLUMN $.name TO $.newName;
@if $.defaultValue!='' && $.defaultValue!=null
    ALTER TABLE $.tableName MODIFY $.newName DEFAULT '$.defaultValue';
@/if
@if $.comment!='' && $.comment!=null
    COMMENT ON COLUMN $.tableName.$.newName IS '$.comment';
@/if

-- 获取数据库表外键信息
-- @sql oracle_queryForeignsByTableName
SELECT
    c.OWNER AS CONSTRAINT_SCHEMA,
    c.CONSTRAINT_NAME,
    'FOREIGN KEY' AS CONSTRAINT_TYPE,
    NULL AS ENFORCED,
    c.TABLE_NAME,
    cc.COLUMN_NAME,
    r.OWNER AS REFERENCED_TABLE_SCHEMA,
    r.TABLE_NAME AS REFERENCED_TABLE_NAME,
    r.COLUMN_NAME AS REFERENCED_COLUMN_NAME,
    c.UPDATE_RULE,
    c.DELETE_RULE
FROM ALL_CONSTRAINTS c
JOIN ALL_CONS_COLUMNS cc ON c.OWNER = cc.OWNER AND c.CONSTRAINT_NAME = cc.CONSTRAINT_NAME
JOIN ALL_CONS_COLUMNS r ON c.R_OWNER = r.OWNER AND c.R_CONSTRAINT_NAME = r.CONSTRAINT_NAME
WHERE 1 = 1
  AND c.CONSTRAINT_TYPE = 'R'
  AND c.TABLE_NAME = '$.tableName';
