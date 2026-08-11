package cn.geelato.web.platform.srv.ocr.invoice.entity;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 发票 OCR 结构化识别结果。
 * <p>包含发票关键字段（阶段一覆盖：代码/号码/日期/金额/税额/价税合计/购销方/经办人），
 * 以及 OCR 原始文本行（raw）供排查。</p>
 *
 * @author geelato
 */
@Data
public class InvoiceOcrResult {

    // ---- 票头基本信息 ----

    /** 发票类型（如"增值税电子普通发票"，抽不到则为 null）。 */
    private String invoiceType;

    /** 发票代码。 */
    private String invoiceCode;

    /** 发票号码。 */
    private String invoiceNumber;

    /** 开票日期。 */
    private String invoiceDate;

    /** 校验码。 */
    private String checkCode;

    // ---- 金额 ----

    /** 金额（不含税）。 */
    private String amount;

    /** 税额。 */
    private String taxAmount;

    /** 价税合计。 */
    private String totalAmount;

    // ---- 购方 ----

    /** 购买方名称。 */
    private String buyerName;

    /** 购买方纳税人识别号。 */
    private String buyerTaxNo;

    // ---- 销方 ----

    /** 销售方名称。 */
    private String sellerName;

    /** 销售方纳税人识别号。 */
    private String sellerTaxNo;

    // ---- 经办人 ----

    /** 收款人。 */
    private String payee;

    /** 复核。 */
    private String reviewer;

    /** 开票人。 */
    private String drawer;

    // ---- 明细（阶段一预留） ----

    /** 货物明细列表（阶段一不填充）。 */
    private List<InvoiceOcrItem> items = new ArrayList<>();

    // ---- 原始结果（raw） ----

    /** OCR 全文（各行用 \n 连接）。 */
    private String fullText;

    /** OCR 原始文本行（含坐标与置信度）。 */
    private List<OcrLine> lines = new ArrayList<>();
}
