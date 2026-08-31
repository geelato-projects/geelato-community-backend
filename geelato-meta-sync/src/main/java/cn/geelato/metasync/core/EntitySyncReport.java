package cn.geelato.metasync.core;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单个实体的三方同步报告（以选定基准源的星型对比）。
 * <p>
 * {@code baseline} 指定基准源（java/meta/table），差异标记在<b>非基准源</b>的列上，
 * 存于 {@code diffsBySource}（key=非基准源 tag，value=该源相对基准的差异列表）。
 *
 * @author geemeta
 */
@Getter
@Setter
public class EntitySyncReport {
    /** 物理表名，作为三方对齐主键 */
    private String tableName;
    /** 实体名（platform_dev_table.entity_name） */
    private String entityName;
    /** 实体标题 */
    private String title;

    /** 基准源：java=Java类 / meta=实体定义 / table=物理表 */
    private String baseline;

    /** 表类型：entity=表 / view=视图（视图不可补偿） */
    private String tableType = "entity";

    /** Java 类源是否存在（含字段数） */
    private SourceStatus javaSource;
    /** 实体定义源是否存在 */
    private SourceStatus metaSource;
    /** 物理表源是否存在 */
    private SourceStatus tableSource;

    /** 非基准源 → 相对基准的差异列表（key: java/meta/table，不含基准自身） */
    private Map<String, List<FieldDiff>> diffsBySource = new LinkedHashMap<>();

    /** 是否一致：三源都存在且所有非基准源无差异 */
    private boolean consistent;

    @Getter
    @Setter
    public static class SourceStatus {
        /** 是否存在 */
        private boolean present;
        /** 字段数量 */
        private int fieldCount;
        /** 存在时的简要描述（类全名 / entityName / 表名） */
        private String detail;

        public SourceStatus(boolean present, int fieldCount, String detail) {
            this.present = present;
            this.fieldCount = fieldCount;
            this.detail = detail;
        }

        public static SourceStatus absent() {
            return new SourceStatus(false, 0, null);
        }
    }

    public void addDiffs(String sourceTag, List<FieldDiff> diffs) {
        if (diffs != null && !diffs.isEmpty()) {
            this.diffsBySource.put(sourceTag, diffs);
        } else {
            this.diffsBySource.put(sourceTag, new ArrayList<>());
        }
    }
}
