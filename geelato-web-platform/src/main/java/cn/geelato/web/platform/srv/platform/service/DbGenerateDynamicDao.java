package cn.geelato.web.platform.srv.platform.service;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.enums.Dialects;
import cn.geelato.core.enums.EnableStatusEnum;
import cn.geelato.core.meta.model.column.ColumnMeta;
import cn.geelato.core.meta.model.connect.ConnectMeta;
import cn.geelato.core.meta.model.entity.TableCheck;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.core.meta.schema.SchemaCheck;
import cn.geelato.core.meta.schema.SchemaForeign;
import cn.geelato.core.meta.schema.SchemaIndex;
import cn.geelato.core.orm.Dao;
import cn.geelato.core.util.ConnectUtils;
import cn.geelato.utils.SqlParams;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
public class DbGenerateDynamicDao {
    @Autowired
    @Qualifier("dynamicDao")
    private Dao dao;

    public DbGenerateDynamicDao() {
    }

    /**
     * 比较字段,并更新数据库
     *
     * @param isExistsTable   表是否存在
     * @param tableMeta       表元数据
     * @param columnMetas     字段
     * @param tableChecks     表检查
     * @param schemaColumnMap 库表字段
     * @param schemaCheckList 库表检查
     * @param schemaIndexList 库表约束
     */
    public Map<String, Object> compareFields(boolean isExistsTable, TableMeta tableMeta, List<ColumnMeta> columnMetas, Collection<TableCheck> tableChecks,
                                             Map<String, Object> schemaColumnMap, List<SchemaCheck> schemaCheckList, List<SchemaIndex> schemaIndexList) {
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
        // 是否有删除字段
        boolean hasDelStatus = false;
        boolean hasDelAt = false;
        // 遍历实体元数据中的字段元数据
        if (columnMetas != null && !columnMetas.isEmpty()) {
            for (ColumnMeta meta : columnMetas) {
                // 跳过禁用的字段
                if (meta.getEnableStatus() == EnableStatusEnum.DISABLED.getValue()) {
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
                        if (schemaColumnMap != null && schemaColumnMap.containsKey(meta.getName()) || (Strings.isNotBlank(meta.getBefColName()) && schemaColumnMap.containsKey(meta.getBefColName()))) {
                            if (schemaColumnMap.containsKey(meta.getName())) {
                                modifyList.add(jsonColumn);
                            } else if (Strings.isNotBlank(meta.getBefColName()) && schemaColumnMap.containsKey(meta.getBefColName())) {
                                changeList.add(jsonColumn);
                            }
                        } else {
                            // 更新时，需要添加的
                            if (meta.getDelStatus() == ColumnDefault.DEL_STATUS_VALUE) {
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
                        if (meta.getDelStatus() == ColumnDefault.DEL_STATUS_VALUE) {
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
        }
        // 唯一约束是否需要添加del
        hasDelStatus = hasDelStatus && hasDelAt;
        // 表检查 - 需要删除的
        ArrayList<JSONObject> delCheckList = new ArrayList<>();
        List<String> delCheckCodes = new ArrayList<>();
        if (schemaCheckList != null && !schemaCheckList.isEmpty()) {
            for (SchemaCheck tc : schemaCheckList) {
                if (!delCheckCodes.contains(tc.getConstraintName())) {
                    delCheckList.add(JSONObject.parseObject(JSONObject.toJSONString(tc)));
                    delCheckCodes.add(tc.getConstraintName());
                }
            }
        }
        // 表检查 - 需要添加的
        ArrayList<JSONObject> checkList = new ArrayList<>();
        if (tableChecks != null && !tableChecks.isEmpty()) {
            for (TableCheck tc : tableChecks) {
                if (tc.getEnableStatus() == EnableStatusEnum.DISABLED.getValue()) {
                    continue;
                }
                checkList.add(JSONObject.parseObject(JSONObject.toJSONString(tc)));
            }
        }
        if (isExistsTable) {
            // 唯一约束索引
            List<String> keyNames = new ArrayList<>();
            if (schemaIndexList != null && !schemaIndexList.isEmpty()) {
                for (SchemaIndex meta : schemaIndexList) {
                    JSONObject jsonColumn = JSONObject.parseObject(JSONObject.toJSONString(meta));
                    if (!keyNames.contains(meta.getKeyName())) {
                        keyNames.add(meta.getKeyName());
                        indexList.add(jsonColumn);
                    }
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
        map.put("tableId", tableMeta.getId());
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
        map.put("tableId", tableMeta.getId());
        map.put("tableName", tableMeta.dbTableName());
        map.put("tableTitle", tableMeta.getTitle());
        map.put("tableSchema", tableMeta.getTableSchema());
        // 表字段 - 新增
        map.put("addList", addList);
        addList.sort(Comparator.comparingInt(o -> o.getIntValue("ordinalPosition")));
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
     * @param primaryDao    默认dao
     * @param isExistsTable 是否存在表
     * @param dbType        数据库类型
     * @param map           参数
     */
    public void createOrUpdateTable(Dao primaryDao, boolean isExistsTable, String dbType, Map<String, Object> map) {
        if (!isExistsTable) {
            // 创建表
            dao.execute(dbType + "_createTable", map);
            // 更新数据库元数据
            primaryDao.execute("upgradeMetaAfterCreateTable", map);
        } else {
            // 更新表
            dao.execute(dbType + "_upgradeTable", map);
            // 更新数据库元数据
            primaryDao.execute("upgradeMetaAfterUpdateTable", map);
        }
    }


    public List<Map<String, Object>> dbQueryAllTables(String dbType, String tableName) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (StringUtils.isNoneBlank(dbType, tableName)) {
            list = dao.queryForMapList(dbType + "_queryAllTables", SqlParams.map("condition", " AND TABLE_NAME='" + tableName + "'"));
        }
        return list;
    }

    public Map<String, Object> dbQueryColumnMapByTableName(String dbType, String tableName) {
        Map<String, Object> map = new HashMap<>();
        List<Map<String, Object>> list = dbQueryColumnList(dbType, tableName, null);
        if (list != null && !list.isEmpty()) {
            for (Map<String, Object> dbCol : list) {
                map.put(dbCol.get("COLUMN_NAME").toString(), dbCol);
            }
        }
        return map;
    }

    public List<Map<String, Object>> dbQueryColumnList(String dbType, String tableName, String columnName) {
        List<Map<String, Object>> list = new LinkedList<>();
        if (StringUtils.isNoneBlank(dbType, tableName)) {
            list = dao.queryForMapList(dbType + "_queryColumnsByTableName", SqlParams.map("tableName", tableName, "columnName", columnName));
        }
        return list;
    }

    public List<SchemaIndex> dbQueryUniqueIndexesByTableName(String dbType, String tableName) {
        List<SchemaIndex> list = new ArrayList<>();
        if (StringUtils.isNoneBlank(dbType, tableName)) {
            try {
                List<Map<String, Object>> mapList = dao.queryForMapList(dbType + "_queryUniqueIndexesByTableName", SqlParams.map("tableName", tableName));
                list.addAll(SchemaIndex.buildData(mapList));
            } catch (Exception e) {
                log.error("查询唯一索引失败", e);
            }
        }
        return list;
    }

    public List<SchemaCheck> dbQueryChecksByTableName(String dbType, String tableName, String constraintName) {
        List<SchemaCheck> list = new ArrayList<>();
        if (StringUtils.isNoneBlank(dbType)) {
            List<Map<String, Object>> mapList = dao.queryForMapList(dbType + "_queryChecksByTableName", SqlParams.map("tableName", tableName, "constraintName", constraintName));
            list.addAll(SchemaCheck.buildData(mapList));
        }
        return list;
    }

    public List<SchemaCheck> dbQueryChecksByConstraintName(String dbType, Collection<TableCheck> tableChecks) {
        List<SchemaCheck> list = new ArrayList<>();
        if (Strings.isNotBlank(dbType) && tableChecks != null && !tableChecks.isEmpty()) {
            List<String> checkNames = tableChecks.stream().map(TableCheck::getCode).collect(Collectors.toList());
            list = dbQueryChecksByTableName(dbType, null, String.join(",", checkNames));
        }
        return list;
    }

    public List<SchemaForeign> dbQueryForeignsByTableName(String dbType, String tableName) {
        List<SchemaForeign> list = new ArrayList<>();
        if (StringUtils.isNoneBlank(dbType, tableName)) {
            List<Map<String, Object>> mapList = dao.queryForMapList(dbType + "_queryForeignsByTableName", SqlParams.map("tableName", tableName));
            list.addAll(SchemaForeign.buildTableForeignKeys(mapList));
        }
        return list;
    }

    public String dbQueryViewsByName(String dbType, String viewName) {
        if (StringUtils.isNoneBlank(dbType, viewName)) {
            try {
                Map<String, Object> result = dao.queryForMap(dbType + "_queryViewsByName", SqlParams.map("viewName", viewName));
                return Dialects.dbViewSql(dbType, result);
            } catch (Exception e) {
                log.error("查询视图失败", e);
            }
        }
        return "";
    }

    public void createOrUpdateView(String dbType, String viewName, String viewSql) {
        if (StringUtils.isAnyBlank(dbType, viewName, viewSql)) {
            throw new IllegalArgumentException("参数（库类型，视图名称，视图语句）不能为空");
        }
        Map<String, Object> map = new HashMap<>();
        map.put("viewName", viewName);
        map.put("viewSql", viewSql);
        dao.execute(dbType + "_createOrReplaceView", map);
    }

    public boolean validateViewSql(ConnectMeta meta, String sql) {
        if (Strings.isBlank(sql)) {
            return false;
        }
        // 使用 try-with-resources 确保资源被正确关闭
        try (Connection conn = ConnectUtils.getConnection(meta)) {
            if (conn == null || !conn.isValid(ConnectUtils.CONNECT_TIMEOUT)) {
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
        } catch (SQLException | ClassNotFoundException e) {
            // 连接或执行过程中出现异常
            return false;
        }
    }
}
