package cn.geelato.web.platform.m.zxing.entity;

import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.core.meta.model.entity.EntityEnableAble;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity(name = "platform_qrcode")
@Title(title = "二维码配置")
public class Qrcode extends BaseEntity implements EntityEnableAble {
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
    @Title(title = "描述")
    private String description;

    @Title(title = "logo base64")
    private String logo;
    @Title(title = "形状", description = "原图，矩形，圆形，圆角矩形")
    @Col(name = "logo_shape")
    private String logoShape;
    @Title(title = "尺寸", description = "2，4，7，11")
    @Col(name = "logo_size")
    private String logoSize;
    @Title(title = "位置", description = "中间，右下角")
    @Col(name = "logo_position")
    private String logoPosition;
    @Title(title = "投影", description = "true，false")
    @Col(name = "logo_projection")
    private Boolean logoProjection;


    @Title(title = "宽度")
    private Integer width;
    @Title(title = "高度")
    private Integer height;
    @Title(title = "背景是否透明")
    private Boolean lucency;

    @Title(title = "码颜色")
    @Col(name = "code_color")
    private String codeColor;
    @Title(title = "码背景颜色")
    @Col(name = "background_color")
    private String backgroundColor;
    @Title(title = "码点形状")
    @Col(name = "code_dot_shape")
    private String codeDotShape;
    @Title(title = "码眼形状")
    @Col(name = "code_eye_shape")
    private String codeEyeShape;
    @Title(title = "码外眼颜色")
    @Col(name = "code_in_eye_color")
    private String codeInEyeColor;
    @Title(title = "码内眼颜色")
    @Col(name = "code_out_eye_color")
    private String codeOutEyeColor;

    @Title(title = "码边距")
    @Col(name = "code_padding")
    private Integer codePadding;
    @Title(title = "容错率")
    @Col(name = "fault_tolerance_rate")
    private String faultToleranceRate;
    @Title(title = "码版本")
    @Col(name = "code_version")
    private String codeVersion;

    @Title(title = "图片格式", description = "png,jpg,jpeg,gif")
    @Col(name = "picture_format")
    private String pictureFormat;
}
