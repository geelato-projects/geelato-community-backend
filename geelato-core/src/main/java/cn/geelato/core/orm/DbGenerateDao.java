package cn.geelato.core.orm;

import cn.geelato.core.constants.MetaDaoSql;
import cn.geelato.core.enums.DeleteStatusEnum;
import cn.geelato.core.enums.EnableStatusEnum;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.column.ColumnMeta;
import cn.geelato.core.meta.model.connect.ConnectMeta;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.entity.TableCheck;
import cn.geelato.core.meta.model.entity.TableForeign;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.core.meta.schema.SchemaCheck;
import cn.geelato.core.meta.schema.SchemaIndex;
import cn.geelato.core.util.ConnectUtils;
import cn.geelato.utils.SqlParams;
import cn.geelato.utils.StringUtils;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author geemeta
 */
@Component
@Slf4j
public class DbGenerateDao {

    private static HashMap<String, Integer> defaultColumnLengthMap;

    @Autowired
    @Qualifier("dynamicDao")
    private Dao dao;

    private final MetaManager metaManager = MetaManager.singleInstance();

    public DbGenerateDao() {
    }


    /**
     * 基于元数据管理，需元数据据管理器已加载、扫描元数据
     * <p>内部调用了sqlId:dropOneTable来删除表，
     * 内部调用了sqlId:createOneTable来创建表</p>
     * 创建完表之后，将元数据信息保存到数据库中
     *
     * @param dropBeforeCreate 存在表时，是否删除
     */
    public void createAllTables(boolean dropBeforeCreate, List<String> ignoreEntityNameList) {
        Collection<EntityMeta> entityMetas = metaManager.getAll();
        if (entityMetas == null) {
            log.warn("实体元数据为空，可能还未解析元数据，请解析之后，再执行该方法(createAllTables)");
            return;
        }
        for (EntityMeta em : entityMetas) {
            boolean isIgnore = false;
            if (ignoreEntityNameList != null) {
                for (String ignoreEntityName : ignoreEntityNameList) {
                    if (em.getEntityName().contains(ignoreEntityName)) {
                        isIgnore = true;
                        break;
                    }
                }
            }
            if (!isIgnore) {
                createOrUpdateOneTable(em, dropBeforeCreate);
            } else {
                log.info("ignore createTable for entity: {}.", em.getEntityName());
            }
        }
        ConnectMeta connectMeta = new ConnectMeta();
        connectMeta.setDbName("geelato");
        connectMeta.setDbConnectName("geelato-local");
        connectMeta.setDbHostnameIp("127.0.0.1");
        connectMeta.setDbUserName("sa");
        connectMeta.setDbPort(3306);
        connectMeta.setDbSchema("geelato");
        connectMeta.setDbType("Mysql");
        connectMeta.setEnableStatus(1);
        connectMeta.setDbPassword("123456");
        Map connectMetaMap = this.dao.save(connectMeta);

        // 保存所有的数据表元数据
        this.saveJavaMetaToDb(connectMetaMap.get("id").toString(), entityMetas);
    }

    /**
     * 将元数据信息保存到服务端数据库。
     * <p>
     * 该方法通常用于开发环境的初始化阶段，在创建完数据库表之后执行。
     * 它遍历传入的实体元数据集合，对每个实体元数据进行处理，包括设置连接ID、链接状态、启用状态等，
     * 并将这些信息保存到服务端数据库中。同时，它还会处理字段元数据和表外键关系，并将它们也保存到数据库中。
     *
     * @param id          连接ID，用于标识数据库连接
     * @param entityMetas 实体元数据集合，包含需要保存的实体元数据
     */
    private void saveJavaMetaToDb(String id, Collection<EntityMeta> entityMetas) {
        for (EntityMeta em : entityMetas) {
            TableMeta tm = em.getTableMeta();
            tm.setConnectId(id);
            tm.setLinked(1);
            tm.setEnableStatus(1);
            Map table = dao.save(tm);
            for (FieldMeta fm : em.getFieldMetas()) {
                ColumnMeta cm = fm.getColumn();
                cm.setTableId(table.get("id").toString());
                cm.setLinked(1);
                dao.save(cm);
            }
            // 保存外键关系
            for (TableForeign ft : em.getTableForeigns()) {
                ft.setEnableStatus(1);
                dao.save(ft);
            }
        }
    }

    private void createOrUpdateOneTable(EntityMeta em, boolean dropBeforeCreate) {

        if (dropBeforeCreate) {
            log.info("  drop entity {}", em.getTableName());
            dao.execute("dropOneTable", SqlParams.map("tableName", em.getTableName()));
        }
        log.info("  create or update an entity {}", em.getTableName());

        // 检查表是否存在，或取已存在的列元数据
        boolean isExistsTable = true;
        Map<String, Object> existsColumnMap = new HashMap<>();
        List<Map<String, Object>> columns = dao.queryForMapList("queryColumnsByTableName", SqlParams.map("tableName", em.getTableName()));
        if (columns == null || columns.isEmpty()) {
            isExistsTable = false;
        } else {
            for (Map<String, Object> columnMap : columns) {
                existsColumnMap.put(columnMap.get("COLUMN_NAME").toString(), columnMap);
            }
        }
        // 通过create table创建的字段
        ArrayList<JSONObject> createList = new ArrayList<>();
        // 通过alert table创建的字段
        ArrayList<JSONObject> addList = new ArrayList<>();
        // 通过alert table修改的字段
        ArrayList<JSONObject> modifyList = new ArrayList<>();
        // 通过alert table删除的字段
        ArrayList<JSONObject> deleteList = new ArrayList<>();
        ArrayList<JSONObject> uniqueList = new ArrayList<>();

        for (FieldMeta fm : em.getFieldMetas()) {
            try {
                if (defaultColumnLengthMap.containsKey(fm.getColumnName())) {
                    int len = defaultColumnLengthMap.get(fm.getColumnName());

                    fm.getColumn().setCharMaxLength(len);
                    fm.getColumn().setNumericPrecision(len);
                    fm.getColumn().afterSet();
                }

                JSONObject jsonColumn = JSONObject.parseObject(JSONObject.toJSONString(fm.getColumn()));

                if (existsColumnMap.containsKey(fm.getColumnName())) {
                    modifyList.add(jsonColumn);
                } else {
                    addList.add(jsonColumn);
                }
                createList.add(jsonColumn);
                if (fm.getColumn().isUniqued()) {
                    uniqueList.add(jsonColumn);
                }
            } catch (Exception e) {
                if (e.getMessage().contains("Duplicate column name")) {
                    log.info("column " + fm.getColumnName() + " is exists，ignore.");
                } else {
                    throw e;
                }
            }
        }
        Map<String, Object> map = new HashMap<>();
        map.put("tableName", em.getTableName());
        map.put("createList", createList);
        map.put("addList", addList);
        map.put("modifyList", modifyList);
        map.put("deleteList", deleteList);
        map.put("uniqueList", uniqueList);
        map.put("foreignList", em.getTableForeigns());
        map.put("existsTable", isExistsTable);

        dao.execute("createOrUpdateOneTable", map);
    }

    /**
     * 比较字段,并更新数据库
     *
     * @param tableMeta       表元数据
     * @param columnMetas     字段
     * @param tableChecks     表检查
     * @param dbColumnMap     库表字段
     * @param schemaCheckList 库表检查
     * @param schemaIndexList 库表约束
     */
    public Map<String, Object> compareFields(TableMeta tableMeta, List<ColumnMeta> columnMetas, Collection<TableCheck> tableChecks,
                                             Map<String, Object> dbColumnMap, List<SchemaCheck> schemaCheckList, List<SchemaIndex> schemaIndexList) {
        // 通过create table创建的字段
        ArrayList<JSONObject> createList = new ArrayList<>();
        // 通过alert table创建的字段
        ArrayList<JSONObject> addList = new ArrayList<>();
        // 通过alert table modify 修改的字段
        ArrayList<JSONObject> modifyList = new ArrayList<>();
        // 通过alert table change 修改的字段
        ArrayList<JSONObject> changeList = new ArrayList<>();
        // 数据表中 非主键的唯一约束索引
        ArrayList<JSONObject> indexList = new ArrayList<>();
        ArrayList<String> primaryList = new ArrayList<>();
        ArrayList<JSONObject> uniqueList = new ArrayList<>();
        // 数据表中 外键
        ArrayList<JSONObject> foreignList = new ArrayList<>();
        ArrayList<JSONObject> delForeignList = new ArrayList<>();
        // 是否存在表
        boolean isExistsTable = dbColumnMap != null && !dbColumnMap.isEmpty();
        // 是否有删除字段
        boolean hasDelStatus = false;
        boolean hasDelAt = false;
        // 遍历实体元数据中的字段元数据
        for (ColumnMeta meta : columnMetas) {
            // 跳过禁用的字段
            if (meta.getEnableStatus() == EnableStatusEnum.DISABLED.getCode()) {
                continue;
            }
            // 同时存在删除状态、删除时间字段时，添加如唯一约束中
            if ("del_status".equals(meta.getName())) {
                hasDelStatus = true;
            }
            if ("delete_at".equals(meta.getName())) {
                hasDelAt = true;
            }
            try {
                JSONObject jsonColumn = JSONObject.parseObject(JSONObject.toJSONString(meta));
                if (isExistsTable) {
                    // 更新时，修改字段
                    if (dbColumnMap.containsKey(meta.getName()) || (Strings.isNotBlank(meta.getBefColName()) && dbColumnMap.containsKey(meta.getBefColName()))) {
                        if (dbColumnMap.containsKey(meta.getName())) {
                            modifyList.add(jsonColumn);
                        } else if (Strings.isNotBlank(meta.getBefColName()) && dbColumnMap.containsKey(meta.getBefColName())) {
                            changeList.add(jsonColumn);
                        }
                    } else {
                        // 更新时，需要添加的
                        if (meta.getDelStatus() == DeleteStatusEnum.NO.getCode()) {
                            addList.add(jsonColumn);
                        }
                    }
                    // primary key
                    if (meta.isKey() && Strings.isNotEmpty(meta.getName())) {
                        primaryList.add(meta.getName());
                    }
                    // unique index
                    if (meta.isUniqued() && !meta.isKey()) {
                        uniqueList.add(jsonColumn);
                    }
                } else {
                    // 表不存在时，创建字段排除删除字段
                    if (meta.getDelStatus() == DeleteStatusEnum.NO.getCode()) {
                        createList.add(jsonColumn);
                    }
                }
            } catch (Exception e) {
                if (e.getMessage().contains("Duplicate column name")) {
                    log.info("column " + meta.getName() + " is exists，ignore.");
                } else {
                    throw e;
                }
            }
        }
        // 唯一约束是否需要添加del
        hasDelStatus = hasDelStatus && hasDelAt;
        // 表检查 - 需要删除的
        ArrayList<JSONObject> delCheckList = new ArrayList<>();
        for (SchemaCheck tc : schemaCheckList) {
            delCheckList.add(JSONObject.parseObject(JSONObject.toJSONString(tc)));
        }
        // 表检查 - 需要添加的
        ArrayList<JSONObject> checkList = new ArrayList<>();
        for (TableCheck tc : tableChecks) {
            if (tc.getEnableStatus() == EnableStatusEnum.DISABLED.getCode()) {
                continue;
            }
            checkList.add(JSONObject.parseObject(JSONObject.toJSONString(tc)));
        }
        if (isExistsTable) {
            // 唯一约束索引
            List<String> keyNames = new ArrayList<>();
            for (SchemaIndex meta : schemaIndexList) {
                JSONObject jsonColumn = JSONObject.parseObject(JSONObject.toJSONString(meta));
                if (!keyNames.contains(meta.getKeyName())) {
                    keyNames.add(meta.getKeyName());
                    indexList.add(jsonColumn);
                }
            }
        }
        // 创建或更新
        if (!isExistsTable) {
            return createTable(tableMeta, createList, delCheckList, checkList, hasDelStatus);
        } else {
            return upgradeTable(tableMeta, addList, modifyList, changeList, primaryList, indexList, uniqueList, delCheckList, checkList, hasDelStatus);
        }
    }

    /**
     * 创建数据库表。
     * 根据提供的表元数据、列信息、外键信息和是否有删除状态标志，创建一个新的数据库表。
     *
     * @param tableMeta        表元数据对象，包含表的基本信息
     * @param createColumnList 包含列信息的JSONObject列表
     * @param delCheckList     包含删除检查信息的JSONObject列表
     * @param checkList        包含检查信息的JSONObject列表
     * @param hasDelStatus     是否有删除状态标志，用于决定是否在表中添加删除状态字段
     */
    private Map<String, Object> createTable(TableMeta tableMeta, List<JSONObject> createColumnList, List<JSONObject> delCheckList, List<JSONObject> checkList, boolean hasDelStatus) {
        Map<String, Object> map = new HashMap<>();
        ArrayList<JSONObject> uniqueColumnList = getUniqueColumn(createColumnList);
        String primaryKey = getPrimaryColumn(createColumnList);
        // 表单信息
        map.put("tableName", tableMeta.getEntityName());
        map.put("tableTitle", tableMeta.getTitle());
        map.put("tableSchema", tableMeta.getTableSchema());
        // 表字段 - 添加
        map.put("addList", createColumnList);
        createColumnList.sort(new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject o1, JSONObject o2) {
                return o1.getIntValue("ordinalPosition") - o2.getIntValue("ordinalPosition");
            }
        });
        // 表索引 - 唯一约束 - 添加
        map.put("uniqueList", uniqueColumnList);
        map.put("hasDelStatus", hasDelStatus);
        // 表索引 - 主键 - 添加
        map.put("primaryKey", primaryKey);
        // 表外键 - 添加
        // map.put("foreignList", foreignList);
        // 表检查 - 添加
        map.put("checkList", checkList);
        // 表检查 - 删除
        map.put("delCheckList", delCheckList);

        return map;
    }

    /**
     * 更新数据库表。
     * 根据提供的表元数据、新增字段列表、修改字段列表、索引列表、唯一约束列表、主键名称以及是否存在删除状态标志，
     * 更新数据库中的表结构。
     *
     * @param tableMeta    表元数据对象，包含表名、表注释等信息
     * @param addList      新增字段列表，每个元素为一个JSONObject，包含字段相关信息
     * @param modifyList   更新字段列表，每个元素为一个JSONObject，包含字段相关信息
     * @param changeList   修改字段列表，每个元素为一个JSONObject，包含字段相关信息
     * @param primaryList  主键列表，每个元素为一个JSONObject，包含主键相关信息
     * @param indexList    索引列表，每个元素为一个JSONObject，包含索引相关信息
     * @param uniqueList   唯一约束列表，每个元素为一个JSONObject，包含唯一约束相关信息
     * @param delCheckList 删除检查列表，每个元素为一个JSONObject，包含删除检查相关信息
     * @param checkList    检查列表，每个元素为一个JSONObject，包含检查相关信息
     * @param hasDelStatus 是否存在删除状态标志
     */
    private Map<String, Object> upgradeTable(TableMeta tableMeta, List<JSONObject> addList, List<JSONObject> modifyList, List<JSONObject> changeList,
                                             List<String> primaryList, List<JSONObject> indexList, List<JSONObject> uniqueList,
                                             List<JSONObject> delCheckList, List<JSONObject> checkList, boolean hasDelStatus) {
        Map<String, Object> map = new HashMap<>();
        // 表单信息
        map.put("tableName", tableMeta.getTableName());
        map.put("tableTitle", tableMeta.getTitle());
        map.put("tableSchema", tableMeta.getTableSchema());
        // 表字段 - 新增
        map.put("addList", addList);
        addList.sort(new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject o1, JSONObject o2) {
                return o1.getIntValue("ordinalPosition") - o2.getIntValue("ordinalPosition");
            }
        });
        // 表字段 - 更新
        map.put("modifyList", modifyList);
        map.put("changeList", changeList);
        // map.put("deleteList", deleteList);
        // 表索引 - 唯一约束 - 删除
        map.put("indexList", indexList);
        // 表索引 - 主键 - 删除、添加
        map.put("primaryKey", String.join(",", primaryList));
        // 表索引 - 唯一约束 - 添加
        map.put("uniqueList", uniqueList);
        map.put("hasDelStatus", hasDelStatus);
        // 表检查 - 添加
        map.put("checkList", checkList);
        // 表检查 - 删除
        map.put("delCheckList", delCheckList);

        return map;
    }

    /**
     * 创建或更新数据库表。执行sql操作
     *
     * @param defaultDao    默认dao
     * @param isExistsTable 是否存在表
     * @param map           参数
     */
    public void createOrUpdateTable(Dao defaultDao, boolean isExistsTable, Map<String, Object> map) {
        if (!isExistsTable) {
            // 删除冲突检查
            dao.execute("deleteConflictingTableChecks", map);
            // 创建表
            dao.execute("createOneTable", map);
            // 更新数据库元数据
            defaultDao.execute("upgradeDbMetaAfterCreate", map);
        } else {
            // 更新表
            dao.execute("upgradeOneTable", map);
            // 更新数据库元数据
            defaultDao.execute("upgradeDbMetaAfterUpdate", map);
        }
    }

    /**
     * 查询指定表模式的检查约束
     *
     * @param tableSchema 表模式
     * @param tableName   表名
     * @param checkList   表检查约束列表
     * @return 包含表检查约束的列表
     */
    public List<SchemaCheck> queryTableChecks(String tableSchema, String tableName, Collection<TableCheck> checkList) {
        List<SchemaCheck> indexList = new ArrayList<>();
        if (Strings.isBlank(tableSchema)) {
            return indexList;
        }
        if (Strings.isNotBlank(tableName)) {
            List<Map<String, Object>> mapList = dao.getJdbcTemplate().queryForList(String.format(MetaDaoSql.SQL_QUERY_TABLE_CONSTRAINTS_BY_TABLE, tableSchema, "CHECK", tableName));
            indexList.addAll(SchemaCheck.buildData(mapList));
        }
        if (checkList != null && !checkList.isEmpty()) {
            List<String> checkNames = checkList.stream().map(TableCheck::getCode).collect(Collectors.toList());
            List<Map<String, Object>> mapList = dao.getJdbcTemplate().queryForList(String.format(MetaDaoSql.SQL_QUERY_TABLE_CONSTRAINTS_BY_NAME, tableSchema, "CHECK", StringUtils.join(checkNames, ",")));
            indexList.addAll(SchemaCheck.buildData(mapList));
        }
        return indexList;
    }

    /**
     * 查询指定表的索引信息
     *
     * @param tableName 表名
     * @return 包含索引信息的列表
     */
    public List<SchemaIndex> queryIndexes(String tableName) {
        List<SchemaIndex> indexList = new ArrayList<>();
        if (Strings.isEmpty(tableName)) {
            return indexList;
        }
        List<Map<String, Object>> mapList = dao.getJdbcTemplate().queryForList(String.format(MetaDaoSql.SQL_INDEXES_NO_PRIMARY, tableName));
        indexList = SchemaIndex.buildData(mapList);
        return indexList;
    }

    public Map<String, Object> getDbTableColumns(String tableSchema, String tableName) {
        Map<String, Object> dbColumnMap = new HashMap<>();
        List<Map<String, Object>> dbColumns = dao.queryForMapList("queryColumnsByTableName", SqlParams.map("tableSchema", tableSchema, "tableName", tableName));
        if (dbColumns != null && !dbColumns.isEmpty()) {
            for (Map<String, Object> dbCol : dbColumns) {
                dbColumnMap.put(dbCol.get("COLUMN_NAME").toString(), dbCol);
            }
        }
        return dbColumnMap;
    }

    private ArrayList<JSONObject> getUniqueColumn(List<JSONObject> jsonObjectList) {
        ArrayList<JSONObject> defaultColumnList = new ArrayList<>();
        for (JSONObject jsonObject : jsonObjectList) {
            if (jsonObject.getBoolean("key")) {
                continue;
            }
            if (jsonObject.getBoolean("uniqued")) {
                defaultColumnList.add(jsonObject);
            }
        }
        return defaultColumnList;
    }

    private String getPrimaryColumn(List<JSONObject> jsonObjectList) {
        Set<String> columnNames = new HashSet<>();
        for (JSONObject jsonObject : jsonObjectList) {
            if (jsonObject.getBoolean("key") && Strings.isNotEmpty(jsonObject.getString("name"))) {
                columnNames.add(jsonObject.getString("name"));
            }
        }
        return String.join(",", columnNames);
    }


    public void createOrUpdateView(String view, String sql) {
        if (Strings.isBlank(view) || Strings.isBlank(sql)) {
            return;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("viewName", view);
        map.put("viewSql", sql);
        dao.execute("createOneView", map);
    }

    public boolean validateViewSql(Dao primaryDao, String connectId, String sql) {
        if (Strings.isBlank(connectId) || Strings.isBlank(sql)) {
            return false;
        }
        // 查询数据库连接信息
        ConnectMeta connectMeta = primaryDao.queryForObject(ConnectMeta.class, connectId);
        if (connectMeta == null) {
            return false;
        }
        // 使用 try-with-resources 确保资源被正确关闭
        try (Connection conn = ConnectUtils.getConnection(connectMeta)) {
            if (conn == null) {
                return false;
            }
            // 设置连接为只读模式，确保不会对数据库数据造成影响
            conn.setReadOnly(true);
            // 使用 PreparedStatement 来验证 SQL 语句
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                // 设置查询超时时间为 60 秒
                stmt.setQueryTimeout(60);
                // 尝试执行 SQL 语句
                stmt.execute();
                return true; // SQL 语句有效
            } catch (SQLException e) {
                // SQL 语句无效
                return false;
            }
        } catch (SQLException e) {
            // 连接或执行过程中出现异常
            return false;
        }
    }
}
