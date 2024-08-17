package cn.geelato.core.orm;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import cn.geelato.core.enums.DeleteStatusEnum;
import cn.geelato.core.enums.EnableStatusEnum;
import cn.geelato.core.enums.TableTypeEnum;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.connect.ConnectMeta;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.entity.TableForeign;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.core.meta.model.field.ColumnMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.core.meta.schema.SchemaIndex;
import cn.geelato.core.util.ConnectUtils;
import cn.geelato.utils.SqlParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

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



    public void createAllTables(boolean dropBeforeCreate) {
        this.createAllTables(dropBeforeCreate, null);
    }

    /**
     * 基于元数据管理，需元数据据管理器已加载、扫描元数据
     * <p>内部调用了sqlId:dropOneTable来删除表，
     * 内部调用了sqlId:createOneTable来创建表</p>
     * 创建完表之后，将元数据信息保存到数据库中
     *
     * @param dropBeforeCreate     存在表时，是否删除
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
     * 将元数据信息保存到服务端，一般用于开发环境初始化，创建完表之后执行
     *
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


    /**
     * 从数据库中删除实体对应的表
     *
     * @param entityName       实体名称
     * @param dropBeforeCreate 存在表时，是否删除
     */
    public void createOrUpdateOneTable(String entityName, boolean dropBeforeCreate) {
        //createOrUpdateOneTable(metaManager.getByEntityName(entityName,false), dropBeforeCreate);
        EntityMeta entityMeta = metaManager.getByEntityName(entityName, false);
        if (TableTypeEnum.TABLE.getCode().equals(entityMeta.getTableMeta().getTableType())) {
            createOrUpdateOneTable(entityMeta);
        } else if (TableTypeEnum.VIEW.getCode().equals(entityMeta.getTableMeta().getTableType())) {
            createOrUpdateView(entityName, entityMeta.getTableMeta().getViewSql());
        }
    }

    private void createOrUpdateOneTable(EntityMeta em) {
        boolean isExistsTable = true;
        Map existscolumnMap = new HashMap();
        String tableName = Strings.isEmpty(em.getTableName()) ? em.getEntityName() : em.getTableName();
        List<Map<String, Object>> columns = dao.queryForMapList("queryColumnsByTableName", SqlParams.map("tableName", tableName));
        if (columns == null || columns.isEmpty()) {
            isExistsTable = false;
        } else {
            for (Map<String, Object> columnMap : columns) existscolumnMap.put(columnMap.get("COLUMN_NAME"), columnMap);
        }
        // 通过create table创建的字段
        ArrayList<JSONObject> createList = new ArrayList<>();
        // 通过alert table创建的字段
        ArrayList<JSONObject> addList = new ArrayList<>();
        // 通过alert table修改的字段
        ArrayList<JSONObject> modifyList = new ArrayList<>();
        // 通过alert table删除的字段
        ArrayList<JSONObject> deleteList = new ArrayList<>();
        // 数据表中 非主键的唯一约束索引
        ArrayList<JSONObject> indexList = new ArrayList<>();
        ArrayList<String> primaryList = new ArrayList<>();
        ArrayList<JSONObject> uniqueList = new ArrayList<>();
        // 数据表中 外键
        ArrayList<JSONObject> foreignList = new ArrayList<>();
        ArrayList<JSONObject> delForeignList = new ArrayList<>();
        // 是否有删除字段
        boolean hasDelStatus = false;
        // 排序
        for (FieldMeta fm : em.getFieldMetas()) {
            if (fm.getColumn().getEnableStatus() == EnableStatusEnum.DISABLED.getCode()) {
                continue;
            }
            // 存在删除字段，用于逻辑删除后，唯一约束问题。
            if ("del_status".equals(fm.getColumn().getName())) {
                hasDelStatus = true;
            }
            try {
                JSONObject jsonColumn = JSONObject.parseObject(JSONObject.toJSONString(fm.getColumn()));
                if (existscolumnMap.containsKey(fm.getColumnName())) {
                    modifyList.add(jsonColumn);
                } else {
                    if (fm.getColumn().getDelStatus() == DeleteStatusEnum.NO.getCode()) {
                        addList.add(jsonColumn);
                    }
                }
                if (fm.getColumn().getDelStatus() == DeleteStatusEnum.NO.getCode()) {
                    createList.add(jsonColumn);
                }
                // primary key
                if (fm.getColumn().isKey() && Strings.isNotEmpty(fm.getColumn().getName())) {
                    primaryList.add(fm.getColumn().getName());
                }
                // unique index
                if (fm.getColumn().isUniqued() && !fm.getColumn().isKey()) {
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
        if (!isExistsTable) {
            createTable(em.getTableMeta(), createList, foreignList, hasDelStatus);
        } else {
            // 唯一约束索引
            List<SchemaIndex> schemaIndexList = metaManager.queryIndexes(em.getTableName());
            List<String> keyNames = new ArrayList<>();
            for (SchemaIndex meta : schemaIndexList) {
                JSONObject jsonColumn = JSONObject.parseObject(JSONObject.toJSONString(meta));
                if (!keyNames.contains(meta.getKeyName())) {
                    indexList.add(jsonColumn);
                    keyNames.add(meta.getKeyName());
                }
            }
            upgradeTable(em.getTableMeta(), addList, modifyList, indexList, uniqueList, String.join(",", primaryList), hasDelStatus);
        }
    }

    private void createOrUpdateOneTable(EntityMeta em, boolean dropBeforeCreate) {

        if (dropBeforeCreate) {
            log.info("  drop entity " + em.getTableName());
            dao.execute("dropOneTable", SqlParams.map("tableName", em.getTableName()));
        }
        log.info("  create or update an entity " + em.getTableName());

        // 检查表是否存在，或取已存在的列元数据
        boolean isExistsTable = true;
        Map existsColumnMap = new HashMap();
        List<Map<String, Object>> columns = dao.queryForMapList("queryColumnsByTableName", SqlParams.map("tableName", em.getTableName()));
        if (columns == null || columns.isEmpty()) {
            isExistsTable = false;
        } else {
            for (Map<String, Object> columnMap : columns) {
                existsColumnMap.put(columnMap.get("COLUMN_NAME"), columnMap);
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
     * 创建数据库表
     *
     */
    private void createTable(TableMeta tableMeta, List<JSONObject> createColumnList, List<JSONObject> foreignList, boolean hasDelStatus) {
        Map<String, Object> map = new HashMap<>();
        ArrayList<JSONObject> uniqueColumnList = getUniqueColumn(createColumnList);
        String primaryKey = getPrimaryColumn(createColumnList);
        // 表单信息
        map.put("tableName", tableMeta.getEntityName());
        map.put("tableTitle", tableMeta.getTitle());
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
        map.put("foreignList", foreignList);
        dao.execute("createOneTable", map);
    }

    /**
     * 更新数据库表
     *
     */
    private void upgradeTable(TableMeta tableMeta, List<JSONObject> addList, List<JSONObject> modifyList, List<JSONObject> indexList,
                              List<JSONObject> uniqueList, String primaryKey, boolean hasDelStatus) {
        Map<String, Object> map = new HashMap<>();
        // 表单信息
        map.put("tableName", tableMeta.getTableName());
        // 表注释 - 更新
        map.put("tableTitle", tableMeta.getTitle());
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
        // map.put("deleteList", deleteList);
        // 表索引 - 唯一约束 - 删除
        map.put("indexList", indexList);
        // 表索引 - 主键 - 删除、添加
        map.put("primaryKey", primaryKey);
        // 表索引 - 唯一约束 - 添加
        map.put("uniqueList", uniqueList);
        map.put("hasDelStatus", hasDelStatus);
        dao.execute("upgradeOneTable", map);
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
        map.put("viewSql", sql);   //TODO 对sql进行检查
        dao.execute("createOneView", map);
    }

    public boolean validateViewSql(String connectId, String sql) throws SQLException {
        if (Strings.isBlank(connectId) || Strings.isBlank(sql)) {
            return false;
        }
        // 查询数据库连接信息
        ConnectMeta connectMeta = dao.queryForObject(ConnectMeta.class, connectId);
        // 创建一个数据库连接对象，但不要打开连接。
        Connection conn = ConnectUtils.getConnection(connectMeta);
        // 创建一个 Statement 对象，但不要执行它。
        Statement stmt = null;
        if (conn != null) {
            stmt = conn.createStatement();
        }
        // 使用 Statement 对象的 setQueryTimeout() 方法设置查询超时时间，以确保 SQL 语句在一定时间内执行完毕。
        // 设置查询超时时间为 60 秒
        if (stmt != null) {
            stmt.setQueryTimeout(60);
        }
        // 使用 Statement 对象的 execute() 方法执行 SQL 语句。如果 SQL 语句正确，execute() 方法将返回 true，否则返回 false。
        boolean isValid = false;
        if (stmt != null) {
            isValid = stmt.execute(sql);
        }
        if (stmt != null) {
            stmt.close();
        }
        if (conn != null) {
            conn.close();
        }

        return isValid;
    }
}
