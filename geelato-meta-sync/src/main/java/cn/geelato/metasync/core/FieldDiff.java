package cn.geelato.metasync.core;

import lombok.Getter;
import lombok.Setter;

/**
 * 某个源相对<b>基准源</b>的单列差异（差异标记在非基准源的列上）。
 * <p>
 * 来源标识：java=Java类，meta=实体定义(platform_dev_column)，table=物理表(INFORMATION_SCHEMA)。
 *
 * @author geemeta
 */
@Getter
@Setter
public class FieldDiff {
    /** 列名（对比 key，优先 columnName） */
    private String columnName;
    /** Java 属性名（驼峰） */
    private String fieldName;
    /** 差异类型 */
    private Status status;
    /** 基准侧归一化基础类型（TYPE_MISMATCH 时填充，如 varchar） */
    private String baselineType;
    /** 本源侧归一化基础类型（TYPE_MISMATCH 时填充，如 int） */
    private String sourceType;
    /** 长度/精度差异描述（LENGTH_DIFF 时填充，仅告警不阻断） */
    private String lengthDiff;

    /**
     * 差异类型枚举。
     */
    public enum Status {
        /** 基准有、本源无 → 本源缺列 */
        MISSING("缺列"),
        /** 基准无、本源有 → 本源多列 */
        EXTRA("多列"),
        /** 同列基础类型不同 */
        TYPE_MISMATCH("类型不匹配"),
        /** 长度/精度差异（告警级） */
        LENGTH_DIFF("长度差异");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }
}
