package cn.geelato.web.platform.srv.ocr.invoice.entity;

import lombok.Data;

/**
 * 发票明细行（货物/劳务）。
 * <p>阶段一预留结构，暂不填充（明细抽取规则后续迭代）。</p>
 *
 * @author geelato
 */
@Data
public class InvoiceOcrItem {

    /** 货物或应税劳务名称。 */
    private String name;

    /** 规格型号。 */
    private String spec;

    /** 单位。 */
    private String unit;

    /** 数量。 */
    private String quantity;

    /** 单价。 */
    private String unitPrice;

    /** 金额。 */
    private String amount;

    /** 税率。 */
    private String taxRate;

    /** 税额。 */
    private String taxAmount;
}
