package cn.geelato.web.platform.srv.ocr.invoice.entity;

import lombok.Data;

/**
 * OCR 识别出的单行文本（原始结果，未做字段抽取）。
 * <p>对应 RapidOCR4j 的 {@code RecResult}：text + 置信度 + 文本框 4 点坐标。
 * 4 点顺序：左上 → 右上 → 右下 → 左下。</p>
 *
 * @author geelato
 */
@Data
public class OcrLine {

    /** 文本内容。 */
    private String text;

    /** 置信度（0~1）。 */
    private float confidence;

    /**
     * 文本框 4 点坐标，长度 8，依次为 [x1,y1, x2,y2, x3,y3, x4,y4]。
     * 用一维数组便于 JSON 序列化。
     */
    private double[] box;

    /** 该行的纵向中心 y 坐标（排序/分区用）。 */
    private double centerY;

    public OcrLine() {
    }

    public OcrLine(String text, float confidence, double[] box) {
        this.text = text;
        this.confidence = confidence;
        this.box = box;
        if (box != null && box.length >= 8) {
            // 4 点 y 坐标均值
            this.centerY = (box[1] + box[3] + box[5] + box[7]) / 4.0;
        }
    }
}
