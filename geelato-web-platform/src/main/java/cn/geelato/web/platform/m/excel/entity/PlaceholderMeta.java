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
@Getter
@Setter
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
