package cn.geelato.web.platform.m.excel.entity;

/**
 * 占位符元数据
 * 占位符示例：${xxx}
 * 用于word或excel的占位符替换
 */
public class PlaceholderMeta {
    // excel或word中看到的占位符
    private String placeholder;
    // 该占位符对应的取值变量
    private String var;
    // 该占位符对应的取值列表变量
    private String listVar;
    // 常量值
    private String constValue;
    // 表达式
    private String expression;
    // STRING | NUMBER | DATE | DATETIME
    private String valueType;
    // VAR变量、CONST常量、EXPRESSION表达式
    private String valueComputeMode;
    // 是否列表中的一项，是否按列表处理
    private boolean isList;
    // 是否需要从上往下同名合并
    private boolean isMerge;
    // 唯一约束，合并时
    private boolean isUnique;
    // 是否是插入图片
    private boolean isImage;
    // 图片宽度，cm
    private Double imageWidth;
    // 图片高度，cm
    private Double imageHeight;

    // 描述
    private String description;

    public String getPlaceholder() {
        return placeholder;
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
    }

    public String getVar() {
        return var;
    }

    public void setVar(String var) {
        this.var = var;
    }

    public String getListVar() {
        return listVar;
    }

    public void setListVar(String listVar) {
        this.listVar = listVar;
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    public String getValueComputeMode() {
        return valueComputeMode;
    }

    public void setValueComputeMode(String valueComputeMode) {
        this.valueComputeMode = valueComputeMode;
    }

    public boolean isIsList() {
        return isList;
    }

    public void setIsList(boolean isList) {
        this.isList = isList;
    }

    public boolean isIsMerge() {
        return isMerge;
    }

    public void setIsMerge(boolean isMerge) {
        this.isMerge = isMerge;
    }

    public boolean isIsUnique() {
        return isUnique;
    }

    public void setIsUnique(boolean isUnique) {
        this.isUnique = isUnique;
    }

    public boolean isIsImage() {
        return isImage;
    }

    public void setIsImage(boolean isImage) {
        this.isImage = isImage;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getConstValue() {
        return constValue;
    }

    public void setConstValue(String constValue) {
        this.constValue = constValue;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    public boolean isValueComputeModeVar() {
        return "VAR".equalsIgnoreCase(this.valueComputeMode);
    }

    public boolean isValueComputeModeConst() {
        return "CONST".equalsIgnoreCase(this.valueComputeMode);
    }

    public boolean isValueComputeModeExpression() {
        return "EXPRESSION".equalsIgnoreCase(this.valueComputeMode);
    }

    public boolean isValueTypeNumber() {
        return "NUMBER".equalsIgnoreCase(this.valueType);
    }

    public boolean isValueTypeDate() {
        return "DATE".equalsIgnoreCase(this.valueType);
    }

    public boolean isValueTypeDateTime() {
        return "DATETIME".equalsIgnoreCase(this.valueType);
    }

    public Double getImageWidth() {
        return imageWidth;
    }

    public void setImageWidth(Double imageWidth) {
        this.imageWidth = imageWidth;
    }

    public Double getImageHeight() {
        return imageHeight;
    }

    public void setImageHeight(Double imageHeight) {
        this.imageHeight = imageHeight;
    }
}
