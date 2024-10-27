package cn.geelato.core.meta.model.view;

import cn.geelato.core.meta.model.column.ColumnMeta;
import lombok.Getter;
import lombok.Setter;

/**
 * @author diabl
 * @description: 视图字段
 */
@Getter
@Setter
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
