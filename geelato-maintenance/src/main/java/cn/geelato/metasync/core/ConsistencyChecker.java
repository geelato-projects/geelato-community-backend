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
 * 三方一致性校验编排（星型基准对比）。
 * <p>
 * 选定一个基准源（java/meta/table），另外两个源各自与基准对比，
 * 差异标记在<b>非基准源</b>的列上（缺列/多列/类型不匹配/长度差异）。
 * 依赖 {@link MetaSourceLoader} 的直接 IO 快照。
 *
 * @author geemeta
 */
public class ConsistencyChecker {

    public static final String BASELINE_JAVA = "java";
    public static final String BASELINE_META = "meta";
    public static final String BASELINE_TABLE = "table";

    private final MetaSourceLoader loader;

    public ConsistencyChecker(MetaSourceLoader loader) {
        this.loader = loader;
    }

    /** 校验基准参数，非法值回退默认 table。 */
    public static String normalizeBaseline(String baseline) {
        if (BASELINE_JAVA.equalsIgnoreCase(baseline)) {
            return BASELINE_JAVA;
        }
        if (BASELINE_META.equalsIgnoreCase(baseline)) {
            return BASELINE_META;
        }
        return BASELINE_TABLE;
    }

    /**
     * 全量校验：装载后，对所有表按指定基准生成报告。
     */
    public List<EntitySyncReport> checkAll(String baseline) {
        String bl = normalizeBaseline(baseline);
        loader.load();
        List<EntitySyncReport> reports = new ArrayList<>();
        for (String tableName : loader.getAllTableNames()) {
            reports.add(check(tableName, bl));
        }
        return reports;
    }

    /**
     * 单实体校验：只装载并校验指定 tableName（不全量扫描）。
     */
    public EntitySyncReport checkSingle(String tableName, String baseline) {
        String bl = normalizeBaseline(baseline);
        loader.loadSingle(tableName);
        return check(tableName, bl);
    }

    /**
     * 对单个 tableName 按基准生成报告（从已装载的快照取三方 EntityMeta）。
     */
    public EntitySyncReport check(String tableName, String baseline) {
        String bl = normalizeBaseline(baseline);
        EntityMeta javaEm = loader.getJavaEntity(tableName);
        EntityMeta metaEm = loader.getMetaEntity(tableName);
        EntityMeta tableEm = loader.getPhysicalEntity(tableName);

        EntitySyncReport report = new EntitySyncReport();
        report.setTableName(tableName);
        report.setBaseline(bl);
        report.setTableType(loader.isView(tableName) ? "view" : "entity");
        report.setEntityName(pickEntityName(javaEm, metaEm, tableEm));
        report.setTitle(pickTitle(javaEm, metaEm, tableEm));

        String javaClassName = loader.getJavaClassName(tableName);
        report.setJavaSource(javaEm != null
                ? new EntitySyncReport.SourceStatus(true, countFields(javaEm),
                javaClassName != null ? javaClassName : javaEm.getEntityName())
                : EntitySyncReport.SourceStatus.absent());
        report.setMetaSource(metaEm != null
                ? new EntitySyncReport.SourceStatus(true, countFields(metaEm), metaEm.getEntityName())
                : EntitySyncReport.SourceStatus.absent());
        report.setTableSource(tableEm != null
                ? new EntitySyncReport.SourceStatus(true, countFields(tableEm), tableName)
                : EntitySyncReport.SourceStatus.absent());

        // 基准源
        EntityMeta baseEm = pick(bl, javaEm, metaEm, tableEm);
        // 另外两个源各自与基准对比，差异标记在本源上
        for (String tag : new String[]{BASELINE_JAVA, BASELINE_META, BASELINE_TABLE}) {
            if (tag.equals(bl)) {
                continue;
            }
            EntityMeta sourceEm = pick(tag, javaEm, metaEm, tableEm);
            report.addDiffs(tag, diffVsBaseline(baseEm, sourceEm));
        }

        // 一致性：三源都存在且所有非基准源无差异
        boolean consistent = javaEm != null && metaEm != null && tableEm != null;
        if (consistent) {
            for (List<FieldDiff> diffs : report.getDiffsBySource().values()) {
                if (diffs != null && !diffs.isEmpty()) {
                    consistent = false;
                    break;
                }
            }
        }
        report.setConsistent(consistent);
        return report;
    }

    private EntityMeta pick(String tag, EntityMeta javaEm, EntityMeta metaEm, EntityMeta tableEm) {
        switch (tag) {
            case BASELINE_JAVA:
                return javaEm;
            case BASELINE_META:
                return metaEm;
            case BASELINE_TABLE:
                return tableEm;
            default:
                return null;
        }
    }

    /**
     * 某源相对基准的差异：以基准列集为参照，差异标记在本源的列上。
     * <ul>
     *   <li>基准有、本源无 → MISSING（本源缺列）</li>
     *   <li>基准无、本源有 → EXTRA（本源多列）</li>
     *   <li>同列基础类型不同 → TYPE_MISMATCH</li>
     *   <li>长度/精度差异 → LENGTH_DIFF（告警）</li>
     * </ul>
     *
     * @param base   基准源（可能为 null：基准整体缺失时所有本源列均标记 EXTRA）
     * @param source 本源（可能为 null：标记为整体缺失，不产出列级差异）
     */
    private List<FieldDiff> diffVsBaseline(EntityMeta base, EntityMeta source) {
        List<FieldDiff> diffs = new ArrayList<>();
        if (source == null) {
            // 本源整体缺失（不产出列级差异，由 SourceStatus.absent 表达；保持空列表）
            return diffs;
        }
        Map<String, FieldMeta> baseMap = indexByColumn(base);
        Map<String, FieldMeta> srcMap = indexByColumn(source);
        // 1. 基准有、本源无 → 本源缺列
        if (baseMap != null) {
            for (Map.Entry<String, FieldMeta> e : baseMap.entrySet()) {
                String col = e.getKey();
                if (srcMap == null || !srcMap.containsKey(col)) {
                    FieldMeta bfm = e.getValue();
                    FieldDiff d = new FieldDiff();
                    d.setColumnName(col);
                    d.setFieldName(bfm.getFieldName());
                    d.setStatus(FieldDiff.Status.MISSING);
                    diffs.add(d);
                }
            }
        }
        if (srcMap == null) {
            return diffs;
        }
        // 2. 本源有、基准无 → 本源多列
        for (Map.Entry<String, FieldMeta> e : srcMap.entrySet()) {
            String col = e.getKey();
            if (baseMap == null || !baseMap.containsKey(col)) {
                FieldMeta sfm = e.getValue();
                FieldDiff d = new FieldDiff();
                d.setColumnName(col);
                d.setFieldName(sfm.getFieldName());
                d.setStatus(FieldDiff.Status.EXTRA);
                diffs.add(d);
            }
        }
        // 3. 两边都有的列：比基础类型、长度
        if (baseMap != null) {
            for (Map.Entry<String, FieldMeta> e : srcMap.entrySet()) {
                String col = e.getKey();
                FieldMeta sfm = e.getValue();
                FieldMeta bfm = baseMap.get(col);
                if (bfm == null || bfm.getColumnMeta() == null || sfm.getColumnMeta() == null) {
                    continue;
                }
                String bt = normalizeBaseType(bfm);
                String st = normalizeBaseType(sfm);
                if (!Objects.equals(bt, st)) {
                    FieldDiff d = new FieldDiff();
                    d.setColumnName(col);
                    d.setFieldName(pickFieldName(sfm, bfm));
                    d.setStatus(FieldDiff.Status.TYPE_MISMATCH);
                    d.setBaselineType(bt);
                    d.setSourceType(st);
                    diffs.add(d);
                    continue;
                }
                String lenDiff = lengthDiff(bfm, sfm);
                if (lenDiff != null) {
                    FieldDiff d = new FieldDiff();
                    d.setColumnName(col);
                    d.setFieldName(pickFieldName(sfm, bfm));
                    d.setStatus(FieldDiff.Status.LENGTH_DIFF);
                    d.setLengthDiff(lenDiff);
                    diffs.add(d);
                }
            }
        }
        return diffs;
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

    private String lengthDiff(FieldMeta bf, FieldMeta sf) {
        long bc = bf.getColumnMeta().getCharMaxLength();
        long sc = sf.getColumnMeta().getCharMaxLength();
        int bp = bf.getColumnMeta().getNumericPrecision();
        int sp = sf.getColumnMeta().getNumericPrecision();
        int bs = bf.getColumnMeta().getNumericScale();
        int ss = sf.getColumnMeta().getNumericScale();
        if (bc != sc) return "charMaxLength: 基准" + bc + " vs 本源" + sc;
        if (bp != sp) return "numericPrecision: 基准" + bp + " vs 本源" + sp;
        if (bs != ss) return "numericScale: 基准" + bs + " vs 本源" + ss;
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
