package cn.geelato.web.platform.m.ocr.entity;

import cn.geelato.plugin.ocr.PDFAnnotationPickContent;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.util.Strings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class OcrPdfContent {
    private String name;
    private String content;
    private Object result;
    private String errorMsg;

    public static List<OcrPdfContent> buildList(List<PDFAnnotationPickContent> pdfAnnotationPickContents) {
        List<OcrPdfContent> list = new ArrayList<>();
        if (pdfAnnotationPickContents != null && !pdfAnnotationPickContents.isEmpty()) {
            for (PDFAnnotationPickContent pdfAnnotationPickContent : pdfAnnotationPickContents) {
                OcrPdfContent opc = new OcrPdfContent();
                opc.setName(pdfAnnotationPickContent.getAnnotationAreaContent());
                opc.setContent(pdfAnnotationPickContent.getInstanceAreaContent());
                opc.setResult(null);
                opc.setErrorMsg(null);
                list.add(opc);
            }
        }
        return list;
    }

    public static Map<String, OcrPdfContent> buildMap(List<PDFAnnotationPickContent> pdfAnnotationPickContents) {
        Map<String, OcrPdfContent> map = new HashMap<>();
        if (pdfAnnotationPickContents != null && !pdfAnnotationPickContents.isEmpty()) {
            for (PDFAnnotationPickContent apc : pdfAnnotationPickContents) {
                OcrPdfContent opc = new OcrPdfContent();
                opc.setName(apc.getAnnotationAreaContent());
                opc.setContent(apc.getInstanceAreaContent());
                opc.setResult(null);
                opc.setErrorMsg(null);
                if (Strings.isNotBlank(opc.getName()) && !map.containsKey(opc.getName())) {
                    map.put(opc.getName(), opc);
                }
            }
        }
        return map;
    }

    public static Map<String, Object> toMap(List<OcrPdfContent> list) {
        Map<String, Object> map = new HashMap<>();
        if (list != null && !list.isEmpty()) {
            for (OcrPdfContent pc : list) {
                if (Strings.isNotBlank(pc.getName()) && !map.containsKey(pc.getName())) {
                    map.put(pc.getName(), pc.getResult() == null ? pc.getContent() : pc.getResult());
                }
            }
        }
        return map;
    }
}
