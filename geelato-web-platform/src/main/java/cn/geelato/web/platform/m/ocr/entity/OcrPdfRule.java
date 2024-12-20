package cn.geelato.web.platform.m.ocr.entity;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class OcrPdfRule {
    private String[] name;
    private String[] expression;
    private Map<String, String> regexp;
}
