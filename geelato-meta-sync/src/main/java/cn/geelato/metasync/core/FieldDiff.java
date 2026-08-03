package cn.geelato.metasync.core;

import lombok.Getter;
import lombok.Setter;

/**
 * 两个字段来源之间的差异。
 * <p>
 * 来源标识：java=Java类，meta=实体定义(platform_dev_column)，table=物理表(INFORMATION_SCHEMA)。
 *
 * @author geemeta
 */
@Getter
@Setter
public class FieldDiff {
    /** 字段名（优先 columnName，回退 fieldName），作为对比 key */
    private String columnName;
    /** Java 属性名（驼峰） */
    private String fieldName;
    /** 类型差异：两侧 dataType（小写归一）不同时填充，否则为 null */
    private TypeDiff typeDiff;
    /** Java 有、对比侧无 → 标记本字段仅 Java 存在 */
    private boolean onlyInJava;
    /** 实体定义有、对比侧无 */
    private boolean onlyInMeta;
    /** 物理表有、对比侧无 */
    private boolean onlyInTable;
    /** 长度/精度差异（仅告警，不阻断） */
    private String lengthDiff;

    @Getter
    @Setter
    public static class TypeDiff {
        /** java 侧基础类型（如 varchar/int），可能为 null（无 Java 源时） */
        private String javaType;
        /** meta 侧基础类型 */
        private String metaType;
        /** table 侧基础类型 */
        private String tableType;

        public TypeDiff(String javaType, String metaType, String tableType) {
            this.javaType = javaType;
            this.metaType = metaType;
            this.tableType = tableType;
        }
    }
}
