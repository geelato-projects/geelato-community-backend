package cn.geelato.web.platform.srv.ocr.invoice.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * 发票 OCR 结构化识别结果（对齐中国发票国家标准）。
 *
 * <h3>标准依据（优先级从高到低）</h3>
 * <ol>
 *   <li>GB/T 42965.1-2023《电子发票业务数据规范 第1部分：基本要素》（税务总局归口）</li>
 *   <li>财政部《电子凭证会计数据标准·全面数字化的电子发票》元素清单（数电票 XML 字段名）</li>
 * </ol>
 *
 * <h3>字段映射表</h3>
 * <pre>
 * | 票面栏次（中文名）   | 输出字段        | 数电票 XML 标签                 |
 * |----------------------|-----------------|---------------------------------|
 * | 发票号码             | invoiceNumber   | EInvoiceNumber（数电票 20 位）  |
 * | 开票日期             | invoiceDate     | IssueDate                       |
 * | 开票金额=价税合计    | totalAmount     | AmountTaxTotal（小写、含税）    |
 * | 备注                 | remark          | Remark                          |
 * | 购买方名称           | buyerName       | BuyerName                       |
 * | 购买方纳税人识别号   | buyerTaxNo      | BuyerTaxRegistrationNumber      |
 * | 销售方名称           | sellerName      | SellerName                      |
 * | 销售方纳税人识别号   | sellerTaxNo     | SellerTaxRegistrationNumber     |
 * | 项目清单             | items           | GoodsInformation（明细行）      |
 * </pre>
 *
 * <p>"开票金额"语义 = 价税合计（小写、含税），与官方查验平台对数电票的录入口径一致
 * （发票号码 + 开票日期 + 价税合计）。</p>
 *
 * @author geelato
 */
@Data
public class InvoiceOcrResult {

    /** 发票号码（数电票为 20 位；EInvoiceNumber / fphm）。 */
    private String invoiceNumber;

    /** 开票日期（IssueDate / kprq；yyyy年mm月dd日 或 yyyy-mm-dd）。 */
    private String invoiceDate;

    /** 开票金额 = 价税合计·小写·含税（AmountTaxTotal / jshj）。 */
    private String totalAmount;

    /** 备注（Remark / bz；票面价税合计行下方的底部区域，可跨多行）。 */
    private String remark;

    /** 购买方名称（BuyerName / gfmc）。 */
    private String buyerName;

    /** 购买方纳税人识别号（BuyerTaxRegistrationNumber / gfsbh；统一社会信用代码同位）。 */
    private String buyerTaxNo;

    /** 销售方名称（SellerName / xfmc）。 */
    private String sellerName;

    /** 销售方纳税人识别号（SellerTaxRegistrationNumber / xfsbh）。 */
    private String sellerTaxNo;

    /**
     * 项目清单：货物或应税劳务、服务明细行（见 {@link InvoiceOcrItem}）。
     * 仅当调用方传 parseItems=true 时填充；否则为 null 且不序列化（返回 JSON 中不出现 items 字段）。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<InvoiceOcrItem> items;
}
