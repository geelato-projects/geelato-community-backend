package cn.geelato.web.platform.m.ocr.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OcrPdfRuleRegExp {
    private String label;
    private String expression;
    private boolean matching = false;
}
