package cn.geelato.web.platform.m.security.entity;

import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.annotation.Transient;
import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.core.meta.model.entity.EntityEnableAble;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Title(title = "读取PDF")
@Entity(name = "platform_ocr_pdf")
public class OcrPdf extends BaseEntity implements EntityEnableAble {
    @Title(title = "应用ID")
    @Col(name = "app_id")
    private String appId;
    @Title(title = "标题")
    private String title;
    @Title(title = "分组编码")
    @Col(name = "group_code")
    private String groupCode;
    @Title(title = "内容")
    private String content;
    @Title(title = "模板")
    private String template;
    @Title(title = "描述")
    private String description;
    @Title(title = "状态")
    @Col(name = "enable_status")
    private int enableStatus;
    @Transient
    private List<OcrPdfMeta> metas;
}
