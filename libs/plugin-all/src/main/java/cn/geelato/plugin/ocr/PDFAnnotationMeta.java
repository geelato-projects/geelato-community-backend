package cn.geelato.plugin.ocr;

import java.util.Map;

/**
 * PDF 注释元数据。
 *
 * @author geelato
 */
public class PDFAnnotationMeta extends AnnotationPositionMeta {

    private String templateAreaContent;
    private String content;
    private Boolean floatArea;
    private Integer lineHeight;
    private Map<String, Object> annotationAttrs;
    private PDFAnnotationDiscernRule pdfAnnotationDiscernRule;

    public String getTemplateAreaContent() {
        return templateAreaContent;
    }

    public void setTemplateAreaContent(String templateAreaContent) {
        this.templateAreaContent = templateAreaContent;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Boolean getFloatArea() {
        return floatArea;
    }

    public void setFloatArea(Boolean floatArea) {
        this.floatArea = floatArea;
    }

    public Integer getLineHeight() {
        return lineHeight;
    }

    public void setLineHeight(Integer lineHeight) {
        this.lineHeight = lineHeight;
    }

    public Map<String, Object> getAnnotationAttrs() {
        return annotationAttrs;
    }

    public void setAnnotationAttrs(Map<String, Object> annotationAttrs) {
        this.annotationAttrs = annotationAttrs;
    }

    public PDFAnnotationDiscernRule getPdfAnnotationDiscernRule() {
        return pdfAnnotationDiscernRule;
    }

    public void setPdfAnnotationDiscernRule(PDFAnnotationDiscernRule pdfAnnotationDiscernRule) {
        this.pdfAnnotationDiscernRule = pdfAnnotationDiscernRule;
    }
}
