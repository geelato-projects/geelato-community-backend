package cn.geelato.plugin.ocr;

import java.util.List;

/**
 * PDF 解析结果数据。
 *
 * @author geelato
 */
public class PDFResolveData {

    private String wholeContent;
    private List<PDFAnnotationPickContent> pdfAnnotationPickContentList;

    public String getWholeContent() {
        return wholeContent;
    }

    public void setWholeContent(String wholeContent) {
        this.wholeContent = wholeContent;
    }

    public List<PDFAnnotationPickContent> getPdfAnnotationPickContentList() {
        return pdfAnnotationPickContentList;
    }

    public void setPdfAnnotationPickContentList(List<PDFAnnotationPickContent> pdfAnnotationPickContentList) {
        this.pdfAnnotationPickContentList = pdfAnnotationPickContentList;
    }
}
