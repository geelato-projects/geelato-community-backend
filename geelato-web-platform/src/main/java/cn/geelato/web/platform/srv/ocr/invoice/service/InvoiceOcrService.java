package cn.geelato.web.platform.srv.ocr.invoice.service;

import cn.geelato.meta.Attachment;
import cn.geelato.utils.FileUtils;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.common.FileHandler;
import cn.geelato.web.platform.srv.ocr.invoice.InvoiceFieldExtractor;
import cn.geelato.web.platform.srv.ocr.invoice.InvoiceOcrEngine;
import cn.geelato.web.platform.srv.ocr.invoice.entity.InvoiceOcrResult;
import cn.geelato.web.platform.srv.ocr.invoice.entity.OcrLine;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.LocationTextExtractionStrategy;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * 发票 OCR 编排服务。
 * <p>按文件后缀分流：{@code .pdf} 走 iText 文本层提取（电子发票 PDF 内嵌真实文本，
 * 无识别误差）；其他（图片）走 {@link InvoiceOcrEngine}（RapidOCR4j）。
 * 文本层为空的扫描件 PDF 无渲染能力（项目无 PDFBox），抛明确错误提示转图片上传。</p>
 *
 * <p>两条路径统一产出 {@link OcrLine} 列表后，交给 {@link InvoiceFieldExtractor}
 * 做字段抽取（PDF 文本行使用虚拟坐标，见 {@link #toLines(String)}）。</p>
 *
 * @author geelato
 */
@Slf4j
@Service
public class InvoiceOcrService {

    /** PDF 虚拟坐标：行距/行高。需大于抽取器行分组阈值（中位行高×0.6）。 */
    private static final double PDF_LINE_HEIGHT = 20.0;
    /** PDF 虚拟坐标：列片段 x 步进与片段宽。 */
    private static final double PDF_CELL_WIDTH = 200.0;

    private final InvoiceOcrEngine engine;
    private final InvoiceFieldExtractor extractor;
    private final FileHandler fileHandler;

    public InvoiceOcrService(InvoiceOcrEngine engine, InvoiceFieldExtractor extractor, FileHandler fileHandler) {
        this.engine = engine;
        this.extractor = extractor;
        this.fileHandler = fileHandler;
    }

    /**
     * 识别并抽取发票字段（按文件后缀自动分流）。
     *
     * @param fileBytes  文件字节
     * @param fileName   原始文件名（用于判断后缀；可为 null，null 时按图片处理）
     * @param parseItems 是否抽取项目清单（明细行），默认不抽取
     * @return 结构化结果
     */
    public InvoiceOcrResult recognize(byte[] fileBytes, String fileName, boolean parseItems) {
        List<OcrLine> lines;
        if (isPdf(fileName)) {
            lines = toLines(extractPdfText(fileBytes));
        } else {
            lines = engine.recognize(fileBytes);
        }
        return extractor.extract(lines, parseItems);
    }

    /**
     * 通过附件 fileId 识别（兼容本地与 OSS；按附件原始文件名分流）。
     *
     * @param fileId     附件 id
     * @param parseItems 是否抽取项目清单（明细行），默认不抽取
     * @return 结构化结果
     */
    public InvoiceOcrResult recognizeByFileId(String fileId, boolean parseItems) {
        if (StringUtils.isBlank(fileId)) {
            throw new IllegalArgumentException("fileId 不能为空");
        }
        Attachment attachment = fileHandler.getAttachment(fileId);
        if (attachment == null) {
            throw new IllegalArgumentException("附件不存在：" + fileId);
        }
        File file = fileHandler.toFile(fileId);
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("文件不存在：" + fileId);
        }
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            return recognize(bytes, attachment.getName(), parseItems);
        } catch (IOException e) {
            throw new RuntimeException("读取文件失败：" + fileId, e);
        }
    }

    /** 引擎健康检查。 */
    public boolean healthCheck() {
        return engine.healthCheck();
    }

    private boolean isPdf(String fileName) {
        return fileName != null && "pdf".equalsIgnoreCase(FileUtils.getFileExtensionWithNoDot(fileName));
    }

    /**
     * 提取 PDF 文本层（iText，按版面坐标排序的策略，适合发票多栏版式）。
     *
     * @param pdfBytes PDF 字节
     * @return 全部页的文本（页间以 \n 连接）
     */
    private String extractPdfText(byte[] pdfBytes) {
        PdfReader reader = null;
        try {
            reader = new PdfReader(pdfBytes);
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= reader.getNumberOfPages(); i++) {
                String pageText = PdfTextExtractor.getTextFromPage(reader, i, new LocationTextExtractionStrategy());
                if (pageText != null && !pageText.isBlank()) {
                    if (sb.length() > 0) {
                        sb.append('\n');
                    }
                    sb.append(pageText.trim());
                }
            }
            String text = sb.toString();
            if (text.replaceAll("\\s", "").length() < 10) {
                throw new IllegalArgumentException("PDF 无文本层（疑似扫描件），请上传发票图片或含文本层的电子发票 PDF");
            }
            return text;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("PDF 文本提取失败：" + e.getMessage(), e);
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    /**
     * PDF 文本行转 {@link OcrLine}（虚拟坐标），使 {@link InvoiceFieldExtractor}
     * 的坐标排序、行分组、片段分配逻辑无改动复用：
     * <ul>
     *   <li>行 i：虚拟 y = i×{@value #PDF_LINE_HEIGHT}（严格递增，行距大于分组阈值）；</li>
     *   <li>行内按连续空白（≥2 空格）拆列片段 j：虚拟 x = j×{@value #PDF_CELL_WIDTH}；</li>
     *   <li>置信度固定 1.0（文本层无识别误差）。</li>
     * </ul>
     */
    private List<OcrLine> toLines(String pdfText) {
        List<OcrLine> lines = new ArrayList<>();
        String[] rawLines = pdfText.split("\\r?\\n");
        for (int i = 0; i < rawLines.length; i++) {
            String row = rawLines[i].trim();
            if (row.isEmpty()) {
                continue;
            }
            // 连续空白视为列分隔（PDF 文本层的多栏行）
            String[] cells = row.split("\\s{2,}");
            for (int j = 0; j < cells.length; j++) {
                String text = cells[j].trim();
                if (text.isEmpty()) {
                    continue;
                }
                double x = j * PDF_CELL_WIDTH;
                double y = i * PDF_LINE_HEIGHT;
                double[] box = {x, y, x + PDF_CELL_WIDTH - 50, y, x + PDF_CELL_WIDTH - 50, y + PDF_LINE_HEIGHT, x, y + PDF_LINE_HEIGHT};
                lines.add(new OcrLine(text, 1.0f, box));
            }
        }
        return lines;
    }
}
