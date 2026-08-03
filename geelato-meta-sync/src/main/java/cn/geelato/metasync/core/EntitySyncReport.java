package cn.geelato.metasync.core;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个实体的三者同步报告。
 *
 * @author geemeta
 */
@Getter
@Setter
public class EntitySyncReport {
    /** 物理表名，作为三方对齐主键 */
    private String tableName;
    /** 实体名（platform_dev_table.entity_name），可能与 tableName 相同 */
    private String entityName;
    /** 实体标题 */
    private String title;

    /** Java 类源是否存在（含字段数） */
    private SourceStatus javaSource;
    /** 实体定义源是否存在（platform_dev_table，含字段数） */
    private SourceStatus metaSource;
    /** 物理表源是否存在（INFORMATION_SCHEMA，含字段数） */
    private SourceStatus tableSource;

    /** Java↔实体定义 字段差异 */
    private List<FieldDiff> javaVsMeta;
    /** 实体定义↔物理表 字段差异 */
    private List<FieldDiff> metaVsTable;
    /** Java↔物理表 字段差异 */
    private List<FieldDiff> javaVsTable;

    /** 是否整体一致（三方字段集与基础类型均匹配） */
    private boolean consistent;

    @Getter
    @Setter
    public static class SourceStatus {
        /** 是否存在 */
        private boolean present;
        /** 字段数量 */
        private int fieldCount;
        /** 存在时的简要描述（如类全名 / entityName / 物理表名） */
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

    public EntitySyncReport() {
        this.javaVsMeta = new ArrayList<>();
        this.metaVsTable = new ArrayList<>();
        this.javaVsTable = new ArrayList<>();
    }
}
