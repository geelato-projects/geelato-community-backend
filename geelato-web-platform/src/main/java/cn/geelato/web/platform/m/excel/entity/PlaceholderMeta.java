package cn.geelato.web.platform.m.excel.entity;

import cn.geelato.web.platform.m.zxing.entity.Barcode;
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
    // 图片来源，附件表，base64，条形码
    @Getter
    @Setter
    private String imageSource;
    // 条形码编号
    @Getter
    @Setter
    private String barcodeCode;
    @Getter
    @Setter
    private Barcode barcode;
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

    public boolean isImageSourceBarcode() {
        return "BARCODE".equalsIgnoreCase(this.imageSource);
    }

    public boolean isImageSourceAttachment() {
        return "ATTACHID".equalsIgnoreCase(this.imageSource);
    }

    public boolean isImageSourceBase64() {
        return "BASE64".equalsIgnoreCase(this.imageSource);
    }

    public boolean isImageSourceRelativePath() {
        return "RELATIVEPATH".equalsIgnoreCase(this.imageSource);
    }

    public boolean isImageSourceNetAddress() {
        return "NETADDRESS".equalsIgnoreCase(this.imageSource);
    }
}
