package cn.geelato.metasync.core;

import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.utils.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 三方一致性校验编排：对每个 tableName 做两两对比，产出 {@link EntitySyncReport}。
 * <p>
 * 依赖 {@link MetaSourceLoader} 的直接 IO 快照，不依赖 MetaManager 全局缓存。
 *
 * @author geemeta
 */
public class ConsistencyChecker {

    private final MetaSourceLoader loader;

    public ConsistencyChecker(MetaSourceLoader loader) {
        this.loader = loader;
    }

    /**
     * 全量校验：执行装载后，对所有表生成报告。
     */
    public List<EntitySyncReport> checkAll() {
        loader.load();
        List<EntitySyncReport> reports = new ArrayList<>();
        for (String tableName : loader.getAllTableNames()) {
            reports.add(check(tableName));
        }
        return reports;
    }

    /**
     * 单实体校验：只装载并校验指定 tableName（不全量扫描，补后立即复验）。
     */
    public EntitySyncReport checkSingle(String tableName) {
        loader.loadSingle(tableName);
        return check(tableName);
    }

    /**
     * 对单个 tableName 生成报告（从已装载的快照取三方 EntityMeta）。
     */
    public EntitySyncReport check(String tableName) {
        EntityMeta javaEm = loader.getJavaEntity(tableName);
        EntityMeta metaEm = loader.getMetaEntity(tableName);
        EntityMeta tableEm = loader.getPhysicalEntity(tableName);

        EntitySyncReport report = new EntitySyncReport();
        report.setTableName(tableName);
        String entityName = pickEntityName(javaEm, metaEm, tableEm);
        report.setEntityName(entityName);
        report.setTitle(pickTitle(javaEm, metaEm, tableEm));

        String javaClassName = loader.getJavaClassName(tableName);
        report.setJavaSource(javaEm != null
                ? new EntitySyncReport.SourceStatus(true, countFields(javaEm),
                javaClassName != null ? javaClassName : (javaEm.getEntityName()))
                : EntitySyncReport.SourceStatus.absent());
        report.setMetaSource(metaEm != null
                ? new EntitySyncReport.SourceStatus(true, countFields(metaEm), metaEm.getEntityName())
                : EntitySyncReport.SourceStatus.absent());
        report.setTableSource(tableEm != null
                ? new EntitySyncReport.SourceStatus(true, countFields(tableEm), tableName)
                : EntitySyncReport.SourceStatus.absent());

        report.setJavaVsMeta(diff(javaEm, metaEm, "java", "meta"));
        report.setMetaVsTable(diff(metaEm, tableEm, "meta", "table"));
        report.setJavaVsTable(diff(javaEm, tableEm, "java", "table"));

        report.setConsistent(isEmpty(report.getJavaVsMeta()) && isEmpty(report.getMetaVsTable()) && isEmpty(report.getJavaVsTable()));
        return report;
    }

    /**
     * 两个来源的字段差异（以 columnName 为 key）。
     */
    private List<FieldDiff> diff(EntityMeta left, EntityMeta right, String leftTag, String rightTag) {
        List<FieldDiff> diffs = new ArrayList<>();
        if (left == null && right == null) {
            return diffs;
        }
        Map<String, FieldMeta> leftMap = indexByColumn(left);
        Map<String, FieldMeta> rightMap = indexByColumn(right);
        Map<String, Boolean> keys = new LinkedHashMap<>();
        if (leftMap != null) {
            for (String k : leftMap.keySet()) {
                keys.put(k, true);
            }
        }
        if (rightMap != null) {
            for (String k : rightMap.keySet()) {
                keys.put(k, true);
            }
        }
        for (String col : keys.keySet()) {
            FieldMeta lf = leftMap == null ? null : leftMap.get(col);
            FieldMeta rf = rightMap == null ? null : rightMap.get(col);
            if (lf != null && rf == null) {
                diffs.add(onlyDiff(col, lf, leftTag));
            } else if (lf == null && rf != null) {
                diffs.add(onlyDiff(col, rf, rightTag));
            } else if (lf != null) {
                String lt = normalizeBaseType(lf);
                String rt = normalizeBaseType(rf);
                if (!Objects.equals(lt, rt)) {
                    FieldDiff d = new FieldDiff();
                    d.setColumnName(col);
                    d.setFieldName(pickFieldName(lf, rf));
                    d.setTypeDiff(buildTypeDiff(lt, rt, leftTag, rightTag));
                    diffs.add(d);
                    continue;
                }
                String lenDiff = lengthDiff(lf, rf);
                if (lenDiff != null) {
                    FieldDiff d = new FieldDiff();
                    d.setColumnName(col);
                    d.setFieldName(pickFieldName(lf, rf));
                    d.setLengthDiff(lenDiff);
                    diffs.add(d);
                }
            }
        }
        return diffs;
    }

    private FieldDiff onlyDiff(String col, FieldMeta fm, String presentTag) {
        FieldDiff d = new FieldDiff();
        d.setColumnName(col);
        d.setFieldName(fm.getFieldName());
        if ("java".equals(presentTag)) {
            d.setOnlyInJava(true);
        } else if ("meta".equals(presentTag)) {
            d.setOnlyInMeta(true);
        } else if ("table".equals(presentTag)) {
            d.setOnlyInTable(true);
        }
        return d;
    }

    /**
     * 归一基础类型，同组视为一致（int 族/varchar 族/decimal 族/blob 族/datetime 族）。
     */
    private String normalizeBaseType(FieldMeta fm) {
        if (fm == null || fm.getColumnMeta() == null) {
            return null;
        }
        String dataType = fm.getColumnMeta().getDataType();
        if (StringUtils.isBlank(dataType)) {
            if (fm.getFieldType() != null) {
                try {
                    dataType = cn.geelato.core.mql.TypeConverter.toSqlTypeString(fm.getFieldType());
                } catch (Exception e) {
                    return null;
                }
            } else {
                return null;
            }
        }
        String t = dataType.toLowerCase(Locale.ENGLISH).trim();
        if (t.equals("int") || t.equals("integer") || t.equals("tinyint") || t.equals("smallint") || t.equals("mediumint")) {
            return "int";
        }
        if (t.equals("char") || t.equals("varchar") || t.equals("text") || t.equals("tinytext") || t.equals("mediumtext") || t.equals("longtext") || t.equals("json")) {
            return "varchar";
        }
        if (t.equals("decimal") || t.equals("numeric") || t.equals("double") || t.equals("float")) {
            return "decimal";
        }
        if (t.equals("blob") || t.equals("tinyblob") || t.equals("mediumblob") || t.equals("longblob")) {
            return "blob";
        }
        if (t.equals("datetime") || t.equals("timestamp")) {
            return "datetime";
        }
        return t;
    }

    private FieldDiff.TypeDiff buildTypeDiff(String left, String right, String leftTag, String rightTag) {
        FieldDiff.TypeDiff td = new FieldDiff.TypeDiff(null, null, null);
        if ("java".equals(leftTag)) td.setJavaType(left);
        else if ("meta".equals(leftTag)) td.setMetaType(left);
        else if ("table".equals(leftTag)) td.setTableType(left);
        if ("java".equals(rightTag)) td.setJavaType(right);
        else if ("meta".equals(rightTag)) td.setMetaType(right);
        else if ("table".equals(rightTag)) td.setTableType(right);
        return td;
    }

    private String lengthDiff(FieldMeta lf, FieldMeta rf) {
        long lc = lf.getColumnMeta().getCharMaxLength();
        long rc = rf.getColumnMeta().getCharMaxLength();
        int lp = lf.getColumnMeta().getNumericPrecision();
        int rp = rf.getColumnMeta().getNumericPrecision();
        int ls = lf.getColumnMeta().getNumericScale();
        int rs = rf.getColumnMeta().getNumericScale();
        if (lc != rc) return "charMaxLength: " + lc + " vs " + rc;
        if (lp != rp) return "numericPrecision: " + lp + " vs " + rp;
        if (ls != rs) return "numericScale: " + ls + " vs " + rs;
        return null;
    }

    private Map<String, FieldMeta> indexByColumn(EntityMeta em) {
        if (em == null || em.getFieldMetas() == null) {
            return null;
        }
        Map<String, FieldMeta> map = new LinkedHashMap<>();
        for (FieldMeta fm : em.getFieldMetas()) {
            String key = fm.getColumnName();
            if (StringUtils.isBlank(key)) {
                key = fm.getFieldName();
            }
            if (StringUtils.isNotBlank(key) && !map.containsKey(key)) {
                map.put(key, fm);
            }
        }
        return map;
    }

    private int countFields(EntityMeta em) {
        return em == null || em.getFieldMetas() == null ? 0 : em.getFieldMetas().size();
    }

    private boolean isEmpty(List<FieldDiff> diffs) {
        return diffs == null || diffs.isEmpty();
    }

    private String pickEntityName(EntityMeta javaEm, EntityMeta metaEm, EntityMeta tableEm) {
        if (metaEm != null && StringUtils.isNotBlank(metaEm.getEntityName())) {
            return metaEm.getEntityName();
        }
        if (javaEm != null && StringUtils.isNotBlank(javaEm.getEntityName())) {
            return javaEm.getEntityName();
        }
        return tableEm == null ? null : tableEm.getEntityName();
    }

    private String pickTitle(EntityMeta javaEm, EntityMeta metaEm, EntityMeta tableEm) {
        if (metaEm != null && StringUtils.isNotBlank(metaEm.getEntityTitle())) {
            return metaEm.getEntityTitle();
        }
        if (javaEm != null && StringUtils.isNotBlank(javaEm.getEntityTitle())) {
            return javaEm.getEntityTitle();
        }
        return null;
    }

    private String pickFieldName(FieldMeta a, FieldMeta b) {
        return StringUtils.isNotBlank(a.getFieldName()) ? a.getFieldName() : b.getFieldName();
    }
}
