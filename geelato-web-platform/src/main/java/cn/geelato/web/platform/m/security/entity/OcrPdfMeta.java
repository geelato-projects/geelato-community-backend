package cn.geelato.web.platform.m.security.entity;

import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Title(title = "PDF批准信息")
@Entity(name = "platform_ocr_pdf_meta")
public class OcrPdfMeta extends BaseEntity {
    @Title(title = "应用ID")
    @Col(name = "app_id")
    private String appId;
    @Title(title = "所属PDF")
    @Col(name = "pdf_id")
    private String pdfId;
    @Title(title = "标题")
    private String title;
    @Title(title = "名称")
    private String name;
    @Title(title = "类型")
    private String type;
    @Title(title = "内容")
    private String content;
    @Title(title = "位置")
    private String position;
    @Title(title = "描述")
    private String description;
}
