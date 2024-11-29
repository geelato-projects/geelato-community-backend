package cn.geelato.web.platform.m.ocr.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OcrPdfMetaRule {
    private String type;
    private String rule;
    private String goal;
    private boolean priority = false;
    private boolean retain = false;
    private long order;
    private String remark;
    private String timeZone;
    private String locale;
    private String extra;
}
