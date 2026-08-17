package cn.geelato.web.platform.srv.ocr.invoice.entity;

import lombok.Data;

/**
 * 发票项目清单明细行（货物或应税劳务、服务）。
 * <p>字段命名对齐 GB/T 42965.1-2023 基本要素与财政部数电票元素清单
 * （GoodsInformation 下的明细数据元）。</p>
 *
 * <h3>字段映射表</h3>
 * <pre>
 * | 票面栏次（中文名）           | 输出字段   | 数电票 XML 标签 |
 * |------------------------------|------------|-----------------|
 * | 货物或应税劳务、服务名称     | name       | ItemName        |
 * | 规格型号                     | spec       | Specification   |
 * | 单位                         | unit       | Unit            |
 * | 数量                         | quantity   | Quantity        |
 * | 单价                         | unitPrice  | Price           |
 * | 金额                         | amount     | Amount          |
 * | 税率                         | taxRate    | TaxRate         |
 * | 税额                         | taxAmount  | TaxAmount       |
 * </pre>
 *
 * @author geelato
 */
@Data
public class InvoiceOcrItem {

    /** 货物或应税劳务、服务名称（ItemName）。 */
    private String name;

    /** 规格型号（Specification）。 */
    private String spec;

    /** 单位（Unit，如 个/件/台）。 */
    private String unit;

    /** 数量（Quantity）。 */
    private String quantity;

    /** 单价（Price）。 */
    private String unitPrice;

    /** 金额（Amount，该行不含税金额）。 */
    private String amount;

    /** 税率（TaxRate，如 13%）。 */
    private String taxRate;

    /** 税额（TaxAmount，该行税额）。 */
    private String taxAmount;
}
