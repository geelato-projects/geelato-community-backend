package cn.geelato.web.platform.srv.ocr.invoice.service;

import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.common.FileHandler;
import cn.geelato.web.platform.srv.ocr.invoice.InvoiceFieldExtractor;
import cn.geelato.web.platform.srv.ocr.invoice.InvoiceOcrEngine;
import cn.geelato.web.platform.srv.ocr.invoice.entity.InvoiceOcrResult;
import cn.geelato.web.platform.srv.ocr.invoice.entity.OcrLine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/**
 * 发票 OCR 编排服务。
 * <p>串联 {@link InvoiceOcrEngine}（识别）与 {@link InvoiceFieldExtractor}（字段抽取），
 * 并提供 fileId 入参（复用 {@link FileHandler}，兼容本地/OSS 附件）。</p>
 *
 * @author geelato
 */
@Slf4j
@Service
public class InvoiceOcrService {

    private final InvoiceOcrEngine engine;
    private final InvoiceFieldExtractor extractor;
    private final FileHandler fileHandler;

    public InvoiceOcrService(InvoiceOcrEngine engine, InvoiceFieldExtractor extractor, FileHandler fileHandler) {
        this.engine = engine;
        this.extractor = extractor;
        this.fileHandler = fileHandler;
    }

    /**
     * 识别图片字节并抽取发票字段。
     *
     * @param imageBytes 图片字节
     * @return 结构化结果（含原始 OCR 行）
     */
    public InvoiceOcrResult recognize(byte[] imageBytes) {
        List<OcrLine> lines = engine.recognize(imageBytes);
        return extractor.extract(lines);
    }

    /**
     * 通过附件 fileId 识别（兼容本地与 OSS）。
     *
     * @param fileId 附件 id
     * @return 结构化结果
     */
    public InvoiceOcrResult recognizeByFileId(String fileId) {
        if (StringUtils.isBlank(fileId)) {
            throw new IllegalArgumentException("fileId 不能为空");
        }
        File file = fileHandler.toFile(fileId);
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("文件不存在：" + fileId);
        }
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            return recognize(bytes);
        } catch (IOException e) {
            throw new RuntimeException("读取文件失败：" + fileId, e);
        }
    }

    /** 引擎健康检查。 */
    public boolean healthCheck() {
        return engine.healthCheck();
    }
}
