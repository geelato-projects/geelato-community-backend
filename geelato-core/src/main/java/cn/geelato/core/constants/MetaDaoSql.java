package cn.geelato.core.constants;

/**
 * @author diabl
 */
public class MetaDaoSql {
    /**
     * 查询 platform_dev_db_connect
     */
    public static final String SQL_CONNECT_LIST = String.format("select * from platform_dev_db_connect where del_status =%d", ColumnDefault.DEL_STATUS_VALUE);
    /**
     * 查询 platform_dev_table
     */
    public static final String SQL_TABLE_LIST = String.format("select * from platform_dev_table where del_status =%d", ColumnDefault.DEL_STATUS_VALUE);
    /**
     * 查询 platform_dev_column
     */
    public static final String SQL_COLUMN_LIST_BY_TABLE = String.format("select * from platform_dev_column where del_status=%d", ColumnDefault.DEL_STATUS_VALUE);
    /**
     * 查询 platform_dev_view
     */
    public static final String SQL_VIEW_LIST_BY_TABLE = String.format("select * from platform_dev_view where del_status=%d", ColumnDefault.DEL_STATUS_VALUE);
    /**
     * 查询 platform_dev_table_foreign
     */
    public static final String SQL_FOREIGN_LIST_BY_TABLE = String.format("select * from platform_dev_table_foreign where del_status=%d", ColumnDefault.DEL_STATUS_VALUE);
    /**
     * 查询 platform_dev_table_check
     */
    public static final String SQL_CHECK_LIST_BY_TABLE = String.format("select * from platform_dev_table_check where del_status=%d", ColumnDefault.DEL_STATUS_VALUE);
    /**
     * 默认视图格式
     */
    public static final String SQL_TABLE_DEFAULT_VIEW = "SELECT %s FROM %s";
    /**
     * 查询 所有表信息
     */
    public static final String INFORMATION_SCHEMA_TABLES = "SELECT * FROM information_schema.tables WHERE 1 = 1 AND TABLE_SCHEMA = %s AND TABLE_TYPE = 'BASE TABLE' %s ORDER BY TABLE_NAME ASC;";
    public static final String INFORMATION_SCHEMA_VIEWS = "SELECT * FROM information_schema.tables WHERE 1 = 1 AND TABLE_SCHEMA = %s AND TABLE_TYPE = 'VIEW' %s ORDER BY TABLE_NAME ASC;";
    public static final String INFORMATION_SCHEMA_COLUMNS = "SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE 1 = 1 AND TABLE_SCHEMA = %s %s ORDER BY ORDINAL_POSITION ASC;";
    /**
     * 查询表单（%s）中非主键的唯一约束索引
     */
    public static final String SQL_INDEXES_NO_PRIMARY = "SHOW INDEXES FROM `%s` WHERE NON_UNIQUE = 0 AND KEY_NAME != 'PRIMARY';";
    public static final String SQL_INDEXES = "SHOW INDEXES FROM `%s`";
    /**
     * 查询表单（%s）中所有外键
     */
    public static final String SQL_FOREIGN_KEY = "SELECT i.TABLE_NAME, i.CONSTRAINT_TYPE, i.CONSTRAINT_NAME, k.REFERENCED_TABLE_NAME, k.REFERENCED_COLUMN_NAME FROM information_schema.TABLE_CONSTRAINTS i LEFT JOIN information_schema.KEY_COLUMN_USAGE k ON i.CONSTRAINT_NAME = k.CONSTRAINT_NAME WHERE i.CONSTRAINT_TYPE = 'FOREIGN KEY' AND i.TABLE_SCHEMA = DATABASE() AND i.TABLE_NAME = '%s';";


    /**
     * 数据库表，修改表信息
     */
    public static final String SQL_ALTER_TABLE = "ALTER TABLE %s COMMENT = '%s';";
    /**
     * 数据库表，复制表信息，不会复制数据。第一个：新表名；第二个：旧表名
     */
    public static final String SQL_COPY_TABLE = "CREATE TABLE %s LIKE %s;";
    /**
     * 数据库表，复制表数据，全插入。第一个：新表名；第二个：旧表名
     */
    public static final String SQL_COPY_TABLE_DATA = "INSERT %s SELECT * FROM %s;";
    /**
     * 数据库表，重命名表，第一个：旧表名；第二个：新表名
     */
    public static final String SQL_RENAME_TABLE = "RENAME TABLE %s TO %s;";
    /**
     * 查询视图信息,第一个：视图名称
     */
    public static final String SQL_SHOW_CREATE_VIEW = "SHOW CREATE VIEW %s;";
    /**
     * 查询表约束信息,第一个：表模式；第二个：约束类型
     */
    public static final String SQL_QUERY_TABLE_CONSTRAINTS = "SELECT t.* FROM (SELECT t1.CONSTRAINT_CATALOG,t1.CONSTRAINT_SCHEMA,t1.TABLE_SCHEMA,t1.TABLE_NAME,t1.CONSTRAINT_TYPE,t1.ENFORCED,t1.CONSTRAINT_NAME,t2.CHECK_CLAUSE FROM information_schema.TABLE_CONSTRAINTS t1 JOIN information_schema.CHECK_CONSTRAINTS t2 ON t1.CONSTRAINT_NAME = t2.CONSTRAINT_NAME WHERE t1.TABLE_SCHEMA = '%s' AND t1.CONSTRAINT_TYPE = '%s') t WHERE 1=1";
    /**
     * 查询表约束信息,第一个：表模式；第二个：约束类型；第三个：约束名称
     */
    public static final String SQL_QUERY_TABLE_CONSTRAINTS_BY_NAME = SQL_QUERY_TABLE_CONSTRAINTS + " AND FIND_IN_SET(CONSTRAINT_NAME, '%s')";
    /**
     * 查询表约束信息,第一个：表模式；第二个：约束类型；第三个：表名称
     */
    public static final String SQL_QUERY_TABLE_CONSTRAINTS_BY_TABLE = SQL_QUERY_TABLE_CONSTRAINTS + " AND TABLE_NAME = '%s'";
}
