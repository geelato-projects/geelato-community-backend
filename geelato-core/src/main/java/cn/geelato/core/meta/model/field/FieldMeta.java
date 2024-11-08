package cn.geelato.core.meta.model.field;

import cn.geelato.core.meta.model.column.ColumnMeta;
import cn.geelato.core.meta.model.entity.EntityMeta;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * @author geemeta
 */
@Getter
@SuppressWarnings("rawtypes")
public class FieldMeta implements Serializable {

    @Setter
    private EntityMeta entityMeta;

    private final ColumnMeta columnMeta;
    /**
     * -- GETTER --
     *  驼峰式
     *  columnMeta.getName()是数据库中的字段格式
     */
    @Setter
    private String fieldName;
    @Setter
    private Class fieldType;

    public FieldMeta(String columnName, String fieldName, String title) {
        columnMeta = new ColumnMeta();
        columnMeta.setName(columnName);
        columnMeta.setTitle(title);
        columnMeta.setFieldName(fieldName);
        this.fieldName = fieldName;
    }
    public ColumnMeta getColumn() {
        return columnMeta;
    }

    public String getColumnName() {
        return columnMeta.getName();
    }

    public void setColumnName(String columnName) {
        this.columnMeta.setName(columnName);
    }

    public String getTitle() {
        return this.getColumn().getTitle();
    }

    public void setTitle(String title) {
        this.getColumn().setTitle(title);
    }


    /**
     * 列名、字段名是否一致
     * 如果columnName或fieldName为空，则返回false
     *
     */
    public boolean isEquals() {
        if (this.getColumn().getName() == null || fieldName == null) {
            return false;
        }
        return this.getColumn().getName().equals(fieldName);
    }

}
