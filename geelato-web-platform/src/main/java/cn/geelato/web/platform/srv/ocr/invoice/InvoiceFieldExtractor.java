package cn.geelato.web.platform.srv.ocr.invoice;

import cn.geelato.web.platform.srv.ocr.invoice.entity.InvoiceOcrResult;
import cn.geelato.web.platform.srv.ocr.invoice.entity.OcrLine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 发票字段抽取器：把 OCR 文本行（含坐标）抽取为结构化字段。
 * <p>基于中文增值税发票版式的关键字定位 + 同行取值 + 正则清洗。
 * 阶段一覆盖：发票代码/号码、开票日期、金额/税额/价税合计、购销方名称与税号、收款人/复核/开票人。
 * 任一字段抽不到不抛异常，留空即可。</p>
 *
 * @author geelato
 */
@Slf4j
@Component
public class InvoiceFieldExtractor {

    // 数字金额（支持 ¥、逗号分隔），如 ¥1,234.56 或 1234.56
    private static final Pattern MONEY = Pattern.compile("[¥￥]?\\s*([\\d,]+(?:\\.\\d+)?)");
    // 纯数字（用于发票代码/号码/税号）
    private static final Pattern DIGITS = Pattern.compile("(\\d{6,})");
    // 日期 yyyy 年 mm 月 dd 日 或 yyyy-mm-dd
    private static final Pattern DATE = Pattern.compile("(\\d{4}\\s*年\\s*\\d{1,2}\\s*月\\s*\\d{1,2}\\s*日|\\d{4}[-/]\\d{1,2}[-/]\\d{1,2})");

    /**
     * 从 OCR 文本行抽取结构化发票字段。
     */
    public InvoiceOcrResult extract(List<OcrLine> lines) {
        InvoiceOcrResult result = new InvoiceOcrResult();
        if (lines == null || lines.isEmpty()) {
            return result;
        }
        result.setLines(lines);
        result.setFullText(joinText(lines));

        // 发票类型：含"增值税"且含"发票"的行（通常在票头）
        for (OcrLine l : lines) {
            String t = l.getText();
            if (t.contains("增值税") && t.contains("发票")) {
                result.setInvoiceType(t.replaceAll("\\s+", "").trim());
                break;
            }
        }

        // 发票代码 / 号码：通常同出现在票头右侧，如 "发票代码 011002100111" / "发票号码 24442000000012345678"
        for (OcrLine l : lines) {
            String t = l.getText();
            if (result.getInvoiceCode() == null && t.contains("发票代码")) {
                result.setInvoiceCode(extractDigits(t));
            }
            if (result.getInvoiceNumber() == null && t.contains("发票号码")) {
                result.setInvoiceNumber(extractDigits(t));
            }
            if (result.getInvoiceCode() != null && result.getInvoiceNumber() != null) {
                break;
            }
        }

        // 开票日期
        for (OcrLine l : lines) {
            if (result.getInvoiceDate() != null) break;
            Matcher m = DATE.matcher(l.getText());
            if (l.getText().contains("开票日期") && m.find()) {
                result.setInvoiceDate(m.group(1).replaceAll("\\s+", ""));
            } else if (m.find() && result.getInvoiceDate() == null) {
                // 兜底：首个日期
                result.setInvoiceDate(m.group(1).replaceAll("\\s+", ""));
            }
        }

        // 金额 / 税额 / 价税合计
        for (OcrLine l : lines) {
            String t = l.getText();
            if (t.contains("价税合计") || t.contains("小写")) {
                if (result.getTotalAmount() == null) {
                    result.setTotalAmount(extractMoney(t));
                }
            }
            if (t.contains("税额") && !t.contains("税率") && result.getTaxAmount() == null) {
                result.setTaxAmount(extractMoney(t));
            }
            if ((t.contains("金额") || t.contains("合计")) && !t.contains("价税") && result.getAmount() == null) {
                String money = extractMoney(t);
                if (money != null) {
                    result.setAmount(money);
                }
            }
        }

        // 购销方：名称与税号。先定位所有"名称:"/"纳税人识别号"出现位置
        int nameIdx = -1;          // 当前匹配到的"名称"所在的行索引
        String[] party = {"", ""}; // [0]=购方 [1]=销方；简化：按出现顺序前两次"名称"分别归购销方
        int partyCount = 0;
        for (int i = 0; i < lines.size(); i++) {
            String t = lines.get(i).getText();
            if (t.contains("名称")) {
                String val = afterColon(t, "名称");
                if (val != null && partyCount < 2) {
                    party[partyCount] = val;
                    partyCount++;
                    nameIdx = i;
                }
            }
            if (t.contains("纳税人识别号")) {
                String val = afterColon(t, "纳税人识别号");
                if (val != null && nameIdx >= 0) {
                    if (partyCount == 1) {
                        result.setBuyerName(party[0]);
                        result.setBuyerTaxNo(val);
                    } else if (partyCount == 2) {
                        result.setSellerName(party[1]);
                        result.setSellerTaxNo(val);
                    }
                }
            }
        }

        // 收款人 / 复核 / 开票人（通常同行或票尾）
        for (OcrLine l : lines) {
            String t = l.getText();
            if (result.getPayee() == null && t.contains("收款人")) {
                result.setPayee(afterColon(t, "收款人"));
            }
            if (result.getReviewer() == null && t.contains("复核")) {
                result.setReviewer(afterColon(t, "复核"));
            }
            if (result.getDrawer() == null && t.contains("开票人")) {
                result.setDrawer(afterColon(t, "开票人"));
            }
        }

        return result;
    }

    /** 提取行内第一段连续数字（6 位以上），用于发票代码/号码/税号。 */
    private String extractDigits(String text) {
        Matcher m = DIGITS.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    /** 提取金额数字部分（去 ¥ 与逗号）。 */
    private String extractMoney(String text) {
        Matcher m = MONEY.matcher(text);
        if (m.find()) {
            return m.group(1).replace(",", "").replace(" ", "");
        }
        return null;
    }

    /** 取关键字之后的值：优先冒号后内容，去掉空白。 */
    private String afterColon(String text, String keyword) {
        int idx = text.indexOf(keyword);
        if (idx < 0) return null;
        String tail = text.substring(idx + keyword.length());
        // 去掉冒号、各种空白
        tail = tail.replaceAll("[:：]", "").trim();
        return tail.isEmpty() ? null : tail;
    }

    private String joinText(List<OcrLine> lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append("\n");
            sb.append(lines.get(i).getText());
        }
        return sb.toString();
    }
}
