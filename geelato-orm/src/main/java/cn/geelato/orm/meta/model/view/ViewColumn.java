package cn.geelato.orm.meta.model.view;

import org.geelato.core.meta.model.field.ColumnMeta;

/**
 * @author diabl
 * @description: 视图字段
 * @date 2023/6/30 9:29
 */
public class ViewColumn {
    private String table_name;
    private String title;
    private String column_name;
    private String field_name;
    private String select_type;
    private String column_comment;
    private Boolean column_key = false;
    private Boolean is_nullable = false;
    private Long character_maxinum_length;
    private Integer numeric_precision;
    private Integer numeric_scale;

    public String getTable_name() {
        return table_name;
    }

    public void setTable_name(String table_name) {
        this.table_name = table_name;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getColumn_name() {
        return column_name;
    }

    public void setColumn_name(String column_name) {
        this.column_name = column_name;
    }

    public String getField_name() {
        return field_name;
    }

    public void setField_name(String field_name) {
        this.field_name = field_name;
    }

    public String getSelect_type() {
        return select_type;
    }

    public void setSelect_type(String select_type) {
        this.select_type = select_type;
    }

    public String getColumn_comment() {
        return column_comment;
    }

    public void setColumn_comment(String column_comment) {
        this.column_comment = column_comment;
    }

    public Boolean getColumn_key() {
        return column_key;
    }

    public void setColumn_key(Boolean column_key) {
        this.column_key = column_key;
    }

    public Boolean getIs_nullable() {
        return is_nullable;
    }

    public void setIs_nullable(Boolean is_nullable) {
        this.is_nullable = is_nullable;
    }

    public Long getCharacter_maxinum_length() {
        return character_maxinum_length;
    }

    public void setCharacter_maxinum_length(Long character_maxinum_length) {
        this.character_maxinum_length = character_maxinum_length;
    }

    public Integer getNumeric_precision() {
        return numeric_precision;
    }

    public void setNumeric_precision(Integer numeric_precision) {
        this.numeric_precision = numeric_precision;
    }

    public Integer getNumeric_scale() {
        return numeric_scale;
    }

    public void setNumeric_scale(Integer numeric_scale) {
        this.numeric_scale = numeric_scale;
    }

    /**
     * 将表格字段转为视图字段
     *
     * @param meta
     * @return
     */
    public static ViewColumn fromColumnMeta(ColumnMeta meta) {
        ViewColumn column = new ViewColumn();
        if (meta != null) {
            column.setTable_name(meta.getTableName());
            column.setTitle(meta.getTitle());
            column.setColumn_name(meta.getName());
            column.setField_name(meta.getFieldName());
            column.setSelect_type(meta.getSelectType());
            column.setColumn_comment(meta.getComment());
            column.setColumn_key(meta.isKey());
            column.setIs_nullable(meta.isNullable());
            column.setCharacter_maxinum_length(meta.getCharMaxLength());
            column.setNumeric_precision(meta.getNumericPrecision());
            column.setNumeric_scale(meta.getNumericScale());
        }

        return column;
    }
}
