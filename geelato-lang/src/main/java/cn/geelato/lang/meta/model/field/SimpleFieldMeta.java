package cn.geelato.lang.meta.model.field;

import java.io.Serializable;


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

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }
}
