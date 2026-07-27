package cn.geelato.ide.entitydef.dto;

import lombok.Data;

/**
 * IDE 实体字段定义。
 * <p>
 * 对应 platform_dev_column 一行业务字段。MVP 不含外键/check/索引。
 *
 * @author geelato
 */
@Data
public class IdeEntityFieldDefinition {

    /** Java 字段名（驼峰），如 customerName */
    private String fieldName;

    /** 数据库列名（snake_case），如 customer_name */
    private String columnName;

    /** 中文标题 */
    private String title;

    /** 数据类型（大写），如 VARCHAR / BIGINT / DECIMAL / DATETIME */
    private String dataType;

    /** 字符长度（dataType=VARCHAR/TEXT 时有效），默认 64 */
    private Long charMaxLength = 64L;

    /** 数字精度（dataType=DECIMAL/INT/BIGINT 时有效），默认 20 */
    private Integer numericPrecision = 20;

    /** 数字小数位（dataType=DECIMAL 时有效），默认 0 */
    private Integer numericScale = 0;

    /** 是否可空，默认 true */
    private Boolean nullable = true;

    /** 是否主键（true 时 platform_dev_column.column_key=b'1'），默认 false */
    private Boolean primaryKey = false;

    /** 是否唯一约束，默认 false */
    private Boolean unique = false;

    /** 是否自增（仅整数主键用），默认 false */
    private Boolean autoIncrement = false;

    /** 默认值 SQL 字面量（如 "'N'"、"0"、"CURRENT_TIMESTAMP"） */
    private String defaultValue;

    /** 是否加密列，默认 false */
    private Boolean encrypted = false;

    /** 列备注 */
    private String comment;
}
