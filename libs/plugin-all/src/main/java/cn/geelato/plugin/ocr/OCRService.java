package cn.geelato.plugin.ocr;

import cn.geelato.plugin.PluginExtensionPoint;

import java.io.File;
import java.util.List;

/**
 * PDF/OCR 解析扩展点。
 * <p>由插件实现，主程序通过 {@code PluginBeanProvider.getBean(OCRService.class, PluginInfo.PluginId)} 获取实例调用。</p>
 *
 * @author geelato
 */
public interface OCRService extends PluginExtensionPoint {

    List<PDFAnnotationMeta> resolvePDFAnnotationMeta(File file);

    List<PDFAnnotationPickContent> pickPDFAnnotationContent(File templateFile, File pdfFile);

    List<PDFAnnotationPickContent> pickPDFAnnotationContent(List<PDFAnnotationMeta> pdfAnnotationMetaList, File pdfFile);

    PDFResolveData resolvePDFFile(File templateFile, File pdfFile);

    PDFResolveData resolvePDFFile(List<PDFAnnotationMeta> pdfAnnotationMetaList, File pdfFile);

    String pickPDFWholeContent(File pdfFile);

    void writeAnnotation(List<PDFAnnotationMeta> pdfAnnotationMetaList, File templateFile, File pdfFile);

    void writeSimpleAnnotation(PDFAnnotationMeta pdfAnnotationMeta, File templateFile, File pdfFile);

    void replaceAnnotationContent(PDFAnnotationMeta pdfAnnotationMeta, String content, File pdfFile);

    void clearContent(List<AnnotationPositionMeta> annotationPositionMetaList, File templateFile, File pdfFile);
}
