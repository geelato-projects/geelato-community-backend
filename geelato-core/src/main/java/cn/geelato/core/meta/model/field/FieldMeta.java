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
     * 判断列名和字段名是否一致。
     * 如果列名（columnName）或字段名（fieldName）为空，则返回false；
     * 否则，比较列名和字段名是否相等，如果相等则返回true，否则返回false。
     *
     * @return 如果列名和字段名一致，则返回true；否则返回false。
     */
    public boolean isEquals() {
        if (this.getColumn().getName() == null || fieldName == null) {
            return false;
        }
        return this.getColumn().getName().equals(fieldName);
    }

}
