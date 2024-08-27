package cn.geelato.web.platform.m.excel.entity;

import lombok.Getter;
import lombok.Setter;

/**
 * 占位符元数据
 * 占位符示例：${xxx}
 * 用于word或excel的占位符替换
 *
 * @author diabl
 */
public class PlaceholderMeta {
    // excel或word中看到的占位符
    @Getter
    @Setter
    private String placeholder;
    // 该占位符对应的取值变量
    @Getter
    @Setter
    private String var;
    // 该占位符对应的取值列表变量
    @Getter
    @Setter
    private String listVar;
    // 常量值
    @Getter
    @Setter
    private String constValue;
    // 表达式
    @Getter
    @Setter
    private String expression;
    // STRING | NUMBER | DATE | DATETIME
    @Getter
    @Setter
    private String valueType;
    // VAR变量、CONST常量、EXPRESSION表达式
    @Getter
    @Setter
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
    @Getter
    @Setter
    private Double imageWidth;
    // 图片高度，cm
    @Getter
    @Setter
    private Double imageHeight;
    // 描述
    @Getter
    @Setter
    private String description;

    public boolean isIsList() {
        return isList;
    }

    public void setIsList(boolean list) {
        isList = list;
    }

    public boolean isIsMerge() {
        return isMerge;
    }

    public void setIsMerge(boolean merge) {
        isMerge = merge;
    }

    public boolean isIsUnique() {
        return isUnique;
    }

    public void setIsUnique(boolean unique) {
        isUnique = unique;
    }

    public boolean isIsImage() {
        return isImage;
    }

    public void setIsImage(boolean image) {
        isImage = image;
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
}
