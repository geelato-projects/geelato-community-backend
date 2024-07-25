package cn.geelato.core.meta.model.field;

import java.io.Serializable;

/**
 * 用于对外发布元数据服务信息，去掉数据库的部分元数据信息，如表名等，详细的字段元数据参见@see FieldMeta
 *
 * @author geemeta
 */
public class SimpleFieldMeta implements Serializable {
    private String name;
    private String type;
    private String title;
    private String comment;
    private boolean nullable;
    private long charMaxLength;
    private int precision;
    private int scale;
    private String selectType;
    private String typeExtra;
    private String extraValue;
    private String extraMap;
    private String defaultValue;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public boolean isNullable() {
        return nullable;
    }

    public void setNullable(boolean nullable) {
        this.nullable = nullable;
    }

    public long getCharMaxLength() {
        return charMaxLength;
    }

    public void setCharMaxLength(long charMaxLength) {
        this.charMaxLength = charMaxLength;
    }

    public int getPrecision() {
        return precision;
    }

    public void setPrecision(int precision) {
        this.precision = precision;
    }

    public int getScale() {
        return scale;
    }

    public void setScale(int scale) {
        this.scale = scale;
    }

    public String getSelectType() {
        return selectType;
    }

    public void setSelectType(String selectType) {
        this.selectType = selectType;
    }

    public String getTypeExtra() {
        return typeExtra;
    }

    public String getExtraValue() {
        return extraValue;
    }

    public void setExtraValue(String extraValue) {
        this.extraValue = extraValue;
    }

    public void setTypeExtra(String typeExtra) {
        this.typeExtra = typeExtra;
    }

    public String getExtraMap() {
        return extraMap;
    }

    public void setExtraMap(String extraMap) {
        this.extraMap = extraMap;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }
}
