package cn.geelato.web.platform.m.zxing.entity;

import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.core.meta.model.entity.EntityEnableAble;
import cn.geelato.utils.ColorUtils;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.m.zxing.enums.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity(name = "platform_barcode")
@Title(title = "条形码配置")
public class Barcode extends BaseEntity implements EntityEnableAble {
    @Title(title = "应用id")
    @Col(name = "app_id")
    private String appId;
    @Title(title = "标题")
    private String title;
    @Title(title = "编码")
    private String code;
    @Title(title = "状态", description = "1表示启用、0表示未启用")
    @Col(name = "enable_status")
    private int enableStatus;
    @Title(title = "类型")
    private String type;
    @Title(title = "条码宽度")
    private Integer width;
    @Title(title = "条码高度")
    private Integer height;
    @Title(title = "上边距")
    @Col(name = "border_top")
    private Integer borderTop;
    @Title(title = "右边距")
    @Col(name = "border_right")
    private Integer borderRight;
    @Title(title = "下边距")
    @Col(name = "border_bottom")
    private Integer borderBottom;
    @Title(title = "左边距")
    @Col(name = "border_left")
    private Integer borderLeft;
    @Title(title = "背景是否透明")
    private Boolean lucency;
    @Title(title = "背景颜色")
    @Col(name = "background_color")
    private String backgroundColor;
    @Title(title = "是否显示文字")
    @Col(name = "display_text")
    private Boolean displayText;
    @Title(title = "字体类型")
    @Col(name = "font_family")
    private String fontFamily;
    @Title(title = "字体大小")
    @Col(name = "font_size")
    private Integer fontSize;
    @Title(title = "条码与字体之间的距离")
    @Col(name = "font_margin")
    private Integer fontMargin;
    @Title(title = "字体对齐方式", description = "left,right,center")
    @Col(name = "font_align")
    private String fontAlign;
    @Title(title = "字体位置", description = "top,bottom")
    @Col(name = "font_position")
    private String fontPosition;
    @Title(title = "字体样式", description = "normal:正常；bold：粗体；italic：斜体")
    @Col(name = "font_style")
    private String fontStyle;
    @Title(title = "字体颜色")
    @Col(name = "font_color")
    private String fontColor;
    @Title(title = "图片格式", description = "png,jpg,jpeg,gif")
    @Col(name = "picture_format")
    private String pictureFormat;
    @Title(title = "描述")
    private String description;

    @Override
    public void afterSet() {
        // 类型 CODE_128
        this.setType(BarcodeTypeEnum.getEnum(this.type, true));
        // 宽度，默认300，最小10
        this.setWidth(this.width == null ? 300 : (this.width <= 10 ? 10 : this.width));
        // 高度，默认100，最小2
        this.setHeight(this.height == null ? 100 : (this.height <= 2 ? 2 : this.height));
        // 边距，默认0
        this.setBorderTop(this.borderTop == null || this.borderTop < 0 ? 0 : this.borderTop);
        this.setBorderBottom(this.borderBottom == null || this.borderBottom < 0 ? 0 : this.borderBottom);
        this.setBorderRight(this.borderRight == null || this.borderRight < 0 ? 0 : this.borderRight);
        this.setBorderLeft(this.borderLeft == null || this.borderLeft < 0 ? 0 : this.borderLeft);
        // 背景是否透明，默认false
        this.setLucency(this.lucency != null && this.lucency);
        // 背景颜色，默认白色
        this.setBackgroundColor(StringUtils.isBlank(this.backgroundColor) ? ColorUtils.WHITE : this.backgroundColor);
        if (this.lucency == true) {
            this.setBackgroundColor(null);
        }
        // 是否显示文字，默认false
        this.setDisplayText(this.displayText != null && this.displayText);
        // 字体，默认宋体
        this.setFontFamily(StringUtils.isBlank(this.fontFamily) ? "宋体" : this.fontFamily);
        // 字体大小，默认12，最小12，最大60
        this.setFontSize(this.fontSize == null ? 16 : (this.fontSize <= 12 ? 12 : (this.fontSize >= 60 ? 60 : this.fontSize)));
        // 字体与条码之间的距离，默认-7
        this.setFontMargin(this.fontMargin == null || this.fontMargin < -100 ? 0 : this.fontMargin);
        // 字体对齐方式，默认center
        this.setFontAlign(BarcodeFontAlignEnum.getEnum(this.fontAlign, true));
        // 字体位置，默认bottom
        this.setFontPosition(BarcodeFontPositionEnum.getEnum(this.fontPosition, true));
        // 字体样式，默认normal
        this.setFontStyle(BarcodeFontStyleEnum.getEnum(this.fontStyle, true));
        // 字体颜色，默认黑色
        this.setFontColor(StringUtils.isBlank(this.fontColor) ? ColorUtils.BLACK : this.fontColor);
        // 图片格式，默认png
        this.setPictureFormat(BarcodePictureFormatEnum.getEnum(this.pictureFormat, true));
    }
}
