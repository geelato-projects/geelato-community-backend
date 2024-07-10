package cn.geelato.core.meta.model.field;

/**
 * @author diabl
 * @description: 字段可供选择的数据类型
 * @date 2023/6/19 11:04
 */
public class ColumnSelectType {
    private String group;// 分组名称
    private String label;// 标题
    private String value;// 内容
    private String mysql;// 数据类型
    private Boolean disabled = false;// 是否禁用
    private Boolean fixed = false;// 是否固定长度 字符串类型
    private Long extent; // 默认长度 字符串类型
    private Long seqNo;

    private Class java;// Java数据类型对象
    private DataTypeRadius radius;// 取值范围

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Boolean getDisabled() {
        return disabled;
    }

    public void setDisabled(Boolean disabled) {
        this.disabled = disabled;
    }

    public String getMysql() {
        return mysql;
    }

    public void setMysql(String mysql) {
        this.mysql = mysql;
    }

    public Class getJava() {
        return java;
    }

    public void setJava(Class java) {
        this.java = java;
    }

    public DataTypeRadius getRadius() {
        return radius;
    }

    public void setRadius(DataTypeRadius radius) {
        this.radius = radius;
    }

    public Long getSeqNo() {
        return seqNo;
    }

    public void setSeqNo(Long seqNo) {
        this.seqNo = seqNo;
    }

    public Boolean getFixed() {
        return fixed;
    }

    public void setFixed(Boolean fixed) {
        this.fixed = fixed;
    }

    public Long getExtent() {
        return extent;
    }

    public void setExtent(Long extent) {
        this.extent = extent;
    }
}
