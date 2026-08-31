package cn.geelato.metasync.core;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.core.meta.MetaReflex;
import cn.geelato.core.meta.schema.SchemaColumn;
import cn.geelato.core.orm.Dao;
import cn.geelato.lang.meta.Entity;
import cn.geelato.utils.AnnotatedClassScanner;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.utils.SchemaUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * 装载三方元数据来源：Java 类源、实体定义源（platform_dev_table/column）、物理表源（INFORMATION_SCHEMA）。
 * <p>
 * 直接 IO 查询三个源，构造独立的 EntityMeta 快照，不依赖也不污染 {@link cn.geelato.core.meta.MetaManager}
 * 的全局缓存（全局缓存有"先到先得/不覆盖"语义，不适合做无状态校验）。
 * <p>
 * 三方对齐主键统一为 <b>物理表名 tableName</b>（大小写不敏感）。
 *
 * @author geemeta
 */
public class MetaSourceLoader {

    private static final Logger log = LoggerFactory.getLogger(MetaSourceLoader.class);

    private final Dao dao;
    private final JdbcTemplate jdbc;

    /** Java 类扫描包名 */
    private String scanPackage = "cn.geelato";

    /** Java 类源：tableName(小写) → EntityMeta */
    private final Map<String, EntityMeta> javaSourceMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    /** Java 类源：tableName(小写) → 类全限定名（诊断用） */
    private final Map<String, String> javaClassNameMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    /** 实体定义源：tableName(小写) → EntityMeta */
    private final Map<String, EntityMeta> metaSourceMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    /** 物理表源：tableName(小写) → List<SchemaColumn> */
    private final Map<String, List<SchemaColumn>> physicalSchemaMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    /** 视图表名集合（实体定义 table_type=view，或物理库 TABLE_TYPE='VIEW'） */
    private final Map<String, Boolean> viewTableNames = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    /** 物理库 schema 名 */
    private String physicalSchema;

    /** 是否已装载（避免每次都全量扫描；调 load() 重置） */
    private volatile boolean loaded = false;

    public MetaSourceLoader(Dao dao) {
        this.dao = dao;
        this.jdbc = dao.getJdbcTemplate();
    }

    public void setScanPackage(String scanPackage) {
        if (StringUtils.isNotBlank(scanPackage)) {
            String newPkg = scanPackage.trim();
            if (!newPkg.equals(this.scanPackage)) {
                this.scanPackage = newPkg;
                this.loaded = false;
            }
        }
    }

    /**
     * 全量装载三方来源（重置后重新查询）。每次调用都直接查库，反映最新数据。
     */
    public synchronized void load() {
        reset();
        loadJavaSource();
        loadMetaSource();
        loadPhysicalSchema();
        loaded = true;
    }

    /**
     * 单实体装载：只装载指定 tableName 的三个源（不全量扫描 Java 类包）。
     * Java 类源仍需扫描（无法按表名定位类），但只取匹配的一个。
     */
    public synchronized void loadSingle(String tableName) {
        if (StringUtils.isBlank(tableName)) {
            return;
        }
        reset();
        // Java 类源：扫描后只保留目标
        loadJavaSource();
        EntityMeta javaEm = javaSourceMap.get(tableName);
        javaSourceMap.clear();
        javaClassNameMap.clear();
        if (javaEm != null) {
            javaSourceMap.put(javaEm.getTableName(), javaEm);
        }

        // 实体定义源：只查该表
        loadMetaSourceSingle(tableName);

        // 物理表源：只查该表
        loadPhysicalSchemaSingle(tableName);
        loaded = true;
    }

    private void reset() {
        javaSourceMap.clear();
        javaClassNameMap.clear();
        metaSourceMap.clear();
        physicalSchemaMap.clear();
        viewTableNames.clear();
        loaded = false;
    }

    // =================== Java 类源 ===================

    private void loadJavaSource() {
        try {
            List<Class<?>> classes = AnnotatedClassScanner.scan(scanPackage, Entity.class);
            if (classes == null) {
                return;
            }
            for (Class<?> clazz : classes) {
                try {
                    EntityMeta em = MetaReflex.getEntityMeta(clazz);
                    String tableName = em.getTableName();
                    if (StringUtils.isBlank(tableName)) {
                        tableName = MetaReflex.getEntityName(clazz);
                    }
                    if (StringUtils.isNotBlank(tableName)) {
                        // 同名取第一个（避免覆盖），记录类名
                        if (!javaSourceMap.containsKey(tableName)) {
                            javaSourceMap.put(tableName, em);
                            javaClassNameMap.put(tableName, clazz.getName());
                        }
                    }
                } catch (Exception e) {
                    log.debug("解析 Java 实体 {} 失败：{}", clazz.getName(), e.getMessage());
                }
            }
            log.info("Java 类源装载完成：{} 个实体（包 {}）", javaSourceMap.size(), scanPackage);
        } catch (Exception e) {
            log.warn("扫描 Java 实体包 {} 失败：{}", scanPackage, e.getMessage());
        }
    }

    public EntityMeta getJavaEntity(String tableName) {
        ensureLoaded();
        return StringUtils.isBlank(tableName) ? null : javaSourceMap.get(tableName);
    }

    /**
     * 按 entityName 查 Java 类源实体（遍历匹配，entityName 来自 @Entity(name=...)）。
     */
    public EntityMeta getJavaEntityByName(String entityName) {
        ensureLoaded();
        if (StringUtils.isBlank(entityName)) {
            return null;
        }
        for (EntityMeta em : javaSourceMap.values()) {
            if (entityName.equalsIgnoreCase(em.getEntityName())) {
                return em;
            }
        }
        return null;
    }

    public String getJavaClassName(String tableName) {
        return StringUtils.isBlank(tableName) ? null : javaClassNameMap.get(tableName);
    }

    // =================== 实体定义源（platform_dev_table/column） ===================

    private void loadMetaSource() {
        try {
            String tableSql = String.format(
                    "select * from platform_dev_table where del_status = %d", ColumnDefault.DEL_STATUS_VALUE);
            String columnSql = String.format(
                    "select * from platform_dev_column where del_status = %d order by table_id, ordinal_position",
                    ColumnDefault.DEL_STATUS_VALUE);
            List<Map<String, Object>> tableList = jdbc.queryForList(tableSql);
            List<Map<String, Object>> allColumns = jdbc.queryForList(columnSql);
            buildMetaSource(tableList, allColumns);
            log.info("实体定义源装载完成：{} 个实体", metaSourceMap.size());
        } catch (Exception e) {
            log.warn("装载实体定义（platform_dev_table）失败：{}", e.getMessage());
        }
    }

    private void loadMetaSourceSingle(String tableName) {
        try {
            // 实体定义按 table_name 过滤（platform_dev_table.table_name）
            String tableSql = String.format(
                    "select * from platform_dev_table where del_status = %d and table_name = '%s'",
                    ColumnDefault.DEL_STATUS_VALUE, escape(tableName));
            List<Map<String, Object>> tableList = jdbc.queryForList(tableSql);
            if (tableList == null || tableList.isEmpty()) {
                return;
            }
            // 该表的列
            Object tableId = tableList.get(0).get("id");
            String columnSql;
            List<Map<String, Object>> columns;
            if (tableId != null) {
                columnSql = String.format(
                        "select * from platform_dev_column where del_status = %d and table_id = '%s' order by ordinal_position",
                        ColumnDefault.DEL_STATUS_VALUE, tableId.toString());
            } else {
                columnSql = String.format(
                        "select * from platform_dev_column where del_status = %d order by ordinal_position",
                        ColumnDefault.DEL_STATUS_VALUE);
                // 过滤会少，下面 buildMetaSource 按 table_id 关联，单表场景全量也能工作
            }
            columns = jdbc.queryForList(columnSql);
            buildMetaSource(tableList, columns);
        } catch (Exception e) {
            log.warn("装载单实体定义（{}）失败：{}", tableName, e.getMessage());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void buildMetaSource(List<Map<String, Object>> tableList, List<Map<String, Object>> allColumns) {
        if (tableList == null) {
            return;
        }
        for (Map<String, Object> tmap : tableList) {
            try {
                Object tid = tmap.get("id");
                Object ename = tmap.get("entity_name");
                if (tid == null || ename == null) {
                    continue;
                }
                List<Map<String, Object>> cols = new ArrayList<>();
                if (allColumns != null) {
                    for (Map<String, Object> c : allColumns) {
                        Object cid = c.get("table_id");
                        if (cid != null && cid.toString().equals(tid.toString())) {
                            cols.add(c);
                        }
                    }
                }
                if (cols.isEmpty()) {
                    continue;
                }
                EntityMeta em = MetaReflex.getEntityMetaByTable(tmap, cols, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
                String tableName = em.getTableName();
                if (StringUtils.isBlank(tableName)) {
                    tableName = ename.toString();
                }
                metaSourceMap.put(tableName, em);
                // 记录视图（table_type=view）
                Object tableType = tmap.get("table_type");
                if (tableType != null && "view".equalsIgnoreCase(tableType.toString().trim())) {
                    viewTableNames.put(tableName, true);
                }
            } catch (Exception e) {
                log.debug("解析实体定义失败：{}", e.getMessage());
            }
        }
    }

    public EntityMeta getMetaEntity(String tableName) {
        ensureLoaded();
        return StringUtils.isBlank(tableName) ? null : metaSourceMap.get(tableName);
    }

    // =================== 物理表源（INFORMATION_SCHEMA） ===================

    private void loadPhysicalSchema() {
        resolveSchema();
        if (StringUtils.isBlank(physicalSchema)) {
            return;
        }
        String sql = "SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = ? ORDER BY TABLE_NAME, ORDINAL_POSITION";
        fillPhysical(jdbc.queryForList(sql, physicalSchema));
        loadPhysicalViews(null);
        log.info("物理表结构装载完成：{} 张表", physicalSchemaMap.size());
    }

    private void loadPhysicalSchemaSingle(String tableName) {
        resolveSchema();
        if (StringUtils.isBlank(physicalSchema)) {
            return;
        }
        String sql = "SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION";
        fillPhysical(jdbc.queryForList(sql, physicalSchema, tableName));
        loadPhysicalViews(tableName);
    }

    /**
     * 查询物理库视图名（INFORMATION_SCHEMA.TABLES TABLE_TYPE='VIEW'），记录到视图集合。
     * INFORMATION_SCHEMA.COLUMNS 中视图的列也会出现，需由此区分表/视图。
     */
    private void loadPhysicalViews(String singleTable) {
        try {
            String sql = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'VIEW'";
            List<Map<String, Object>> rows;
            if (StringUtils.isNotBlank(singleTable)) {
                rows = jdbc.queryForList(sql + " AND TABLE_NAME = ?", physicalSchema, singleTable);
            } else {
                rows = jdbc.queryForList(sql, physicalSchema);
            }
            if (rows != null) {
                for (Map<String, Object> r : rows) {
                    Object name = r.get("TABLE_NAME");
                    if (name != null) {
                        viewTableNames.put(name.toString(), true);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("查询物理视图失败：{}", e.getMessage());
        }
    }

    private void resolveSchema() {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT DATABASE() AS SCHEMA_NAME");
            physicalSchema = (rows != null && !rows.isEmpty() && rows.get(0).get("SCHEMA_NAME") != null)
                    ? rows.get(0).get("SCHEMA_NAME").toString() : null;
        } catch (Exception e) {
            physicalSchema = null;
        }
    }

    private void fillPhysical(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<SchemaColumn> all = SchemaUtils.buildData(SchemaColumn.class, rows);
        for (SchemaColumn sc : all) {
            if (StringUtils.isBlank(sc.getTableName())) {
                continue;
            }
            physicalSchemaMap.computeIfAbsent(sc.getTableName(), k -> new ArrayList<>()).add(sc);
        }
    }

    public EntityMeta getPhysicalEntity(String tableName) {
        ensureLoaded();
        List<SchemaColumn> cols = StringUtils.isBlank(tableName) ? null : physicalSchemaMap.get(tableName);
        if (cols == null || cols.isEmpty()) {
            return null;
        }
        return PhysicalEntityBuilder.build(tableName, cols);
    }

    // =================== 公共 ===================

    /**
     * 是否为视图（实体定义 table_type=view，或物理库 TABLE_TYPE='VIEW'）。
     */
    public boolean isView(String tableName) {
        ensureLoaded();
        return StringUtils.isNotBlank(tableName) && viewTableNames.containsKey(tableName);
    }

    /**
     * 三方 tableName 并集。
     */
    public List<String> getAllTableNames() {
        ensureLoaded();
        Map<String, Boolean> all = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String t : physicalSchemaMap.keySet()) {
            all.put(t, true);
        }
        for (String t : javaSourceMap.keySet()) {
            all.put(t, true);
        }
        for (String t : metaSourceMap.keySet()) {
            all.put(t, true);
        }
        return new ArrayList<>(all.keySet());
    }

    private void ensureLoaded() {
        if (!loaded) {
            load();
        }
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("'", "''");
    }
}
