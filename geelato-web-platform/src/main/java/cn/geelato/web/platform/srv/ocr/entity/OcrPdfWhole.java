package cn.geelato.web.platform.srv.ocr.entity;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class OcrPdfWhole {
    private String wholeContent;
    private List<OcrPdfContent> ocrPdfContents;
}
