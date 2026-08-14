package cn.geelato.web.platform.srv.ocr.invoice;

import cn.geelato.web.platform.srv.ocr.invoice.entity.OcrLine;
import io.github.hzkitty.RapidOCR;
import io.github.hzkitty.entity.OcrResult;
import io.github.hzkitty.entity.RecResult;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Point;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 发票 OCR 引擎封装（基于 RapidOCR4j）。
 * <p>单例持有 {@link RapidOCR} 实例，加载其内置的 PP-OCRv4 中文模型（det+cls+rec）。
 * RapidOCR 实例非线程安全，故 {@link #recognize(byte[])} 用 synchronized 保护。</p>
 *
 * <p>native 依赖：onnxruntime 1.18.0 + openpnp opencv 4.6.0-0（均自带全平台 native，
 * 由 rapidocr4j 传递依赖引入）。若 OpenCV native 加载失败，可经
 * {@code geelato.ocr.invoice.opencv-lib-path} 指向外置 dll/so。</p>
 *
 * @author geelato
 */
@Slf4j
@Component
public class InvoiceOcrEngine {

    private volatile RapidOCR ocr;

    private volatile boolean ready = false;

    private final ExecutorService warmUpExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "invoice-ocr-warmer");
        t.setDaemon(true);
        return t;
    });

    @Value("${geelato.ocr.invoice.opencv-lib-path:}")
    private String opencvLibPath;

    @Value("${geelato.ocr.invoice.enabled:true}")
    private boolean enabled;

    /**
     * OCR 模型加载策略：true（默认）启动期不同步加载，改为就绪后异步预热 + 首次识别懒加载，
     * 避免加载 native 库与 AI 模型阻塞应用启动；false 恢复启动期同步加载的旧行为。
     */
    @Value("${geelato.ocr.invoice.async-init:true}")
    private boolean asyncInit;

    /**
     * 启动期处理：仅当关闭异步预热（asyncInit=false）时同步加载模型，恢复历史行为；
     * 否则不做同步加载，等待 {@link #warmUp(ApplicationReadyEvent)} 异步预热或 {@link #recognize(byte[])} 懒加载。
     */
    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("发票 OCR 已禁用（geelato.ocr.invoice.enabled=false）");
            return;
        }
        if (!asyncInit) {
            buildOcr();
        } else {
            log.info("发票 OCR 启用异步预热，启动期不加载模型");
        }
    }

    /**
     * 应用就绪后异步预热 OCR 模型（不阻塞启动链路）。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp(ApplicationReadyEvent event) {
        if (!enabled || !asyncInit) {
            return;
        }
        warmUpExecutor.submit(() -> {
            try {
                buildOcr();
            } catch (Throwable t) {
                log.error("发票 OCR 异步预热失败（不阻断应用）", t);
            }
        });
    }

    /**
     * 同步加载 RapidOCR 实例（加载模型与 native 库），已加载则直接返回。
     * <p>失败不抛出（标记 ready=false），避免阻断应用启动——与插件加载容错策略一致。
     * 调用时若未就绪则抛 IllegalStateException 提示。</p>
     */
    private synchronized void buildOcr() {
        if (!enabled || ocr != null || ready) {
            return;
        }
        try {
            // 默认无参 create() 走 jar 内置 PP-OCRv4 模型；如需外置 opencv native，可在此传 OcrConfig
            this.ocr = RapidOCR.create();
            this.ready = true;
            log.info("发票 OCR 引擎初始化成功（PP-OCRv4 中文模型已加载）");
        } catch (Throwable t) {
            // 用 Throwable 捕获 native 加载的 UnsatisfiedLinkError 等
            log.error("发票 OCR 引擎初始化失败（native 库或模型加载异常，OCR 功能不可用，不阻断应用）", t);
            this.ready = false;
        }
    }

    @PreDestroy
    public void destroy() {
        // RapidOCR 无显式 close；OrtEnvironment 为进程级单例，无需在此释放
        this.ready = false;
        this.ocr = null;
        warmUpExecutor.shutdownNow();
    }

    /**
     * 识别图片，返回 OCR 文本行列表。
     * <p>若引擎尚未加载（异步预热未完成或未触发），此处同步懒加载后再识别。</p>
     *
     * @param imageBytes 图片字节（jpg/png 等）
     * @return 文本行（含坐标与置信度）；引擎未就绪时抛 IllegalStateException
     */
    public synchronized List<OcrLine> recognize(byte[] imageBytes) {
        if (enabled && (!ready || ocr == null)) {
            buildOcr();
        }
        if (!ready || ocr == null) {
            throw new IllegalStateException("发票 OCR 引擎未就绪，请检查启动日志（native/模型加载是否失败）");
        }
        if (imageBytes == null || imageBytes.length == 0) {
            return new ArrayList<>();
        }
        try {
            OcrResult result = ocr.run(imageBytes);
            return toLines(result);
        } catch (Exception e) {
            log.error("发票 OCR 识别异常", e);
            throw new RuntimeException("发票 OCR 识别失败：" + e.getMessage(), e);
        }
    }

    /** 引擎是否就绪（供健康检查用）。 */
    public boolean healthCheck() {
        return ready;
    }

    private List<OcrLine> toLines(OcrResult result) {
        List<OcrLine> lines = new ArrayList<>();
        if (result == null || result.getRecRes() == null) {
            return lines;
        }
        for (RecResult rec : result.getRecRes()) {
            if (rec == null) {
                continue;
            }
            lines.add(new OcrLine(rec.getText(), rec.getConfidence(), toBoxArray(rec.getDtBoxes())));
        }
        return lines;
    }

    /** 将 OpenCV Point[4] 转为一维 double[8]。 */
    private double[] toBoxArray(Point[] points) {
        if (points == null || points.length < 4) {
            return new double[8];
        }
        double[] box = new double[8];
        for (int i = 0; i < 4; i++) {
            box[i * 2] = points[i].x;
            box[i * 2 + 1] = points[i].y;
        }
        return box;
    }
}
