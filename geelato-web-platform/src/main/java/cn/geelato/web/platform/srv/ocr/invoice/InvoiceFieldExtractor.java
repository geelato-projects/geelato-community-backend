package cn.geelato.web.platform.srv.ocr.invoice;

import cn.geelato.web.platform.srv.ocr.invoice.entity.InvoiceOcrItem;
import cn.geelato.web.platform.srv.ocr.invoice.entity.InvoiceOcrResult;
import cn.geelato.web.platform.srv.ocr.invoice.entity.OcrLine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 发票字段抽取器：把 OCR 文本行（含坐标）抽取为对齐国家标准的结构化字段。
 * <p>字段语义依据 GB/T 42965.1-2023《电子发票业务数据规范 第1部分：基本要素》，
 * 输出仅含：发票号码、开票日期、开票金额（价税合计）、备注、购销方名称与纳税人识别号，
 * 以及可选的项目清单（明细行，调用方传 parseItems=true 时）。</p>
 *
 * <h3>提取与校验策略</h3>
 * <ul>
 *   <li><b>标签感知切分</b>：PDF 分栏（购/销方左右布局）被 iText 合并成一行时，
 *       一个标签的值截断到<b>下一个标签</b>为止，避免串值（如两个公司名拼在一起）。</li>
 *   <li><b>国标格式校验</b>（宁缺勿错）：
 *       纳税人识别号按 GB 32100-2015（18 位统一社会信用代码，字符集数字+大写字母不含 I/O/S/V/Z）
 *       或 15 位数字旧税号校验；不合规值自动清洗提取合规子串，仍无则置 null；
 *       发票号码限 8 位（纸票）或 20 位（数电票）。</li>
 * </ul>
 *
 * <p>抽取基于中文增值税发票版式：先按坐标排序（上到下、左到右），再关键字定位 +
 * 同行/邻行取值 + 正则清洗。任一字段抽不到不抛异常，留空即可。</p>
 *
 * @author geelato
 */
@Slf4j
@Component
public class InvoiceFieldExtractor {

    /** 数字金额（支持 ¥、逗号分隔），如 ¥1,234.56 或 1234.56。 */
    private static final Pattern MONEY = Pattern.compile("[¥￥]?\\s*([\\d,]+(?:\\.\\d+)?)");
    /** 日期：yyyy年mm月dd日 或 yyyy-mm-dd / yyyy/mm/dd。 */
    private static final Pattern DATE = Pattern.compile("(\\d{4}\\s*年\\s*\\d{1,2}\\s*月\\s*\\d{1,2}\\s*日|\\d{4}[-/]\\d{1,2}[-/]\\d{1,2})");
    /** 发票号码：20 位（数电票）或 8 位（纸票）纯数字。 */
    private static final Pattern INVOICE_NO = Pattern.compile("(?<!\\d)(\\d{20}|\\d{8})(?!\\d)");
    /** 纳税人识别号（GB 32100-2015）：18 位统一社会信用代码（数字+大写字母，不含 I/O/S/V/Z）。 */
    private static final Pattern TAX_NO_18 = Pattern.compile("(?<![0-9A-Z])[0-9A-HJ-NPQRTUWXY]{18}(?![0-9A-Z])");
    /** 纳税人识别号（旧税号）：15 位纯数字。 */
    private static final Pattern TAX_NO_15 = Pattern.compile("(?<!\\d)\\d{15}(?!\\d)");
    /** 数值单元格（明细行）：可选 ¥ 前缀、逗号、小数、可选 % 后缀。 */
    private static final Pattern NUMERIC_CELL = Pattern.compile("^[¥￥]?[\\d,]+(?:\\.\\d+)?%?$");
    /** 税率单元格（含 %）。 */
    private static final Pattern RATE_CELL = Pattern.compile("^[¥￥]?[\\d,]+(?:\\.\\d+)?%$");
    /** 备注区截止关键字：出现即停止向下收集。 */
    private static final String[] REMARK_STOP = {"购买方", "销售方", "开票人", "收款人", "复核", "密码", "开票日期", "价税合计"};

    /**
     * 票面标签关键字（值截断边界），按长度降序——长标签优先匹配，
     * 避免"统一社会信用代码/纳税人识别号"被拆成两个短标签。
     */
    private static final String[] LABELS = {
            "统一社会信用代码/纳税人识别号", "纳税人识别号", "统一社会信用代码",
            "名称", "地址", "电话", "开户行", "账号", "密码区", "开票日期", "发票号码"
    };

    /**
     * 从 OCR 文本行抽取结构化发票字段。
     *
     * @param lines      OCR 文本行（含坐标）
     * @param parseItems 是否抽取项目清单（明细行），默认不抽取
     * @return 结构化结果
     */
    public InvoiceOcrResult extract(List<OcrLine> lines, boolean parseItems) {
        InvoiceOcrResult result = new InvoiceOcrResult();
        if (lines == null || lines.isEmpty()) {
            return result;
        }
        // 按坐标排序：上到下、左到右（OCR 输出顺序不保证按位置）
        List<OcrLine> sorted = new ArrayList<>(lines);
        sorted.sort(Comparator.comparingDouble(OcrLine::getCenterY).thenComparingDouble(this::centerX));

        extractHeader(sorted, result);
        extractTotalAmount(sorted, result);
        extractParties(sorted, result);
        extractRemark(sorted, result);
        if (parseItems) {
            result.setItems(extractItems(sorted));
        }
        return result;
    }

    // ---- 票头：发票号码 / 开票日期 ----

    private void extractHeader(List<OcrLine> lines, InvoiceOcrResult result) {
        for (OcrLine l : lines) {
            String t = l.getText();
            if (result.getInvoiceNumber() == null && t.contains("发票号码")) {
                // 标签后的值优先，行内兜底；限 20 位（数电票）或 8 位（纸票）
                String tail = t.substring(t.indexOf("发票号码") + "发票号码".length());
                Matcher m = INVOICE_NO.matcher(tail);
                if (m.find()) {
                    result.setInvoiceNumber(m.group(1));
                } else {
                    Matcher fallback = INVOICE_NO.matcher(t);
                    if (fallback.find()) {
                        result.setInvoiceNumber(fallback.group(1));
                    }
                }
            }
        }
        // 开票日期：优先含"开票日期"的行，兜底取首个日期
        for (OcrLine l : lines) {
            Matcher m = DATE.matcher(l.getText());
            if (l.getText().contains("开票日期") && m.find()) {
                result.setInvoiceDate(normalizeInvoiceDate(m.group(1)));
                break;
            }
        }
        if (result.getInvoiceDate() == null) {
            for (OcrLine l : lines) {
                Matcher m = DATE.matcher(l.getText());
                if (m.find()) {
                    result.setInvoiceDate(normalizeInvoiceDate(m.group(1)));
                    break;
                }
            }
        }
    }

    // ---- 开票金额 = 价税合计（小写、含税） ----

    private void extractTotalAmount(List<OcrLine> lines, InvoiceOcrResult result) {
        for (OcrLine l : lines) {
            String t = l.getText();
            if (result.getTotalAmount() != null || !(t.contains("价税合计") || t.contains("小写"))) {
                continue;
            }
            // 优先"小写"后的金额，否则"价税合计"行最后一个金额
            String v = null;
            int xi = t.indexOf("小写");
            if (xi >= 0) {
                v = extractMoney(t.substring(xi));
            }
            if (v == null && t.contains("价税合计")) {
                v = lastMoney(t);
            }
            if (v != null) {
                result.setTotalAmount(v);
            }
        }
        if (result.getTotalAmount() != null) {
            return;
        }
        // 标签行本身无金额（值行与标签行分离）：先向上再向下找相邻金额行。
        // 数电票版式金额行在标签行上方（如"陆仟零肆拾伍圆整 ¥6045.00"），特征为含中文大写"圆整/元整"；
        // 向下兜底要求行以 ¥/￥/?/数字 开头，防止错取备注中"ZIMUSHH32241959"这类字母夹数字串。
        for (int i = 0; i < lines.size(); i++) {
            String t = lines.get(i).getText();
            if (!t.contains("价税合计") && !t.contains("小写")) {
                continue;
            }
            for (int j = i - 1; j >= Math.max(0, i - 2); j--) {
                String s = lines.get(j).getText().trim();
                if (s.contains("圆整") || s.contains("元整")) {
                    String v = lastMoney(s);
                    if (v != null) {
                        result.setTotalAmount(v);
                        return;
                    }
                }
            }
            for (int j = i + 1; j < Math.min(lines.size(), i + 3); j++) {
                String s = lines.get(j).getText().trim();
                if (s.isEmpty()) {
                    continue;
                }
                char c = s.charAt(0);
                if (c == '¥' || c == '￥' || c == '?' || Character.isDigit(c)) {
                    String v = extractMoney(s);
                    if (v != null) {
                        result.setTotalAmount(v);
                        return;
                    }
                }
            }
        }
    }

    // ---- 购销方：名称 + 纳税人识别号（标签感知 + 分区归属 + 国标校验） ----

    /**
     * 购销方抽取：
     * <ol>
     *   <li>逐行提取 (标签, 值) 对；行含"购买方/销售方"栏头时，该行的值归属对应分区；</li>
     *   <li>值经 {@link #normalizeName}/{@link #normalizeTaxNo} 国标校验清洗；</li>
     *   <li>装配：分区块（购/销）各自取首个名称与税号；未分区的按出现顺序对位
     *       （names[i] ↔ taxNos[i]，前=购、后=销）补充缺失。</li>
     * </ol>
     */
    private void extractParties(List<OcrLine> lines, InvoiceOcrResult result) {
        List<String> namesNoSection = new ArrayList<>();
        List<String> taxNosNoSection = new ArrayList<>();
        String buyerName = null, buyerTaxNo = null, sellerName = null, sellerTaxNo = null;

        for (OcrLine l : lines) {
            String t = l.getText();
            if (isDetailHeaderRow(t) || t.contains("价税合计")) {
                continue;
            }
            // 分区提示：数电票票面有"购买方""销售方"栏头（栏头可能单独成行，也可能与标签同行）
            boolean buyerRow = t.contains("购买方") || t.contains("购货单位");
            boolean sellerRow = t.contains("销售方") || t.contains("销货单位");

            for (String[] pair : extractLabeledPairs(t)) {
                String label = pair[0];
                String value = pair[1];
                if (isNameLabel(label)) {
                    String name = normalizeName(value);
                    if (name == null) {
                        continue;
                    }
                    if (buyerRow && !sellerRow) {
                        if (buyerName == null) buyerName = name;
                    } else if (sellerRow && !buyerRow) {
                        if (sellerName == null) sellerName = name;
                    } else {
                        namesNoSection.add(name);
                    }
                } else if (isTaxNoLabel(label)) {
                    String taxNo = normalizeTaxNo(value);
                    if (taxNo == null) {
                        continue;
                    }
                    if (buyerRow && !sellerRow) {
                        if (buyerTaxNo == null) buyerTaxNo = taxNo;
                    } else if (sellerRow && !buyerRow) {
                        if (sellerTaxNo == null) sellerTaxNo = taxNo;
                    } else {
                        taxNosNoSection.add(taxNo);
                    }
                }
            }
        }

        // 未分区的按出现顺序对位补充（PDF 分栏合并行内也是先购后销）
        if (buyerName == null && !namesNoSection.isEmpty()) {
            buyerName = namesNoSection.get(0);
        }
        if (buyerTaxNo == null && !taxNosNoSection.isEmpty()) {
            buyerTaxNo = taxNosNoSection.get(0);
        }
        if (sellerName == null && namesNoSection.size() > 1) {
            sellerName = namesNoSection.get(1);
        }
        if (sellerTaxNo == null && taxNosNoSection.size() > 1) {
            sellerTaxNo = taxNosNoSection.get(1);
        }

        result.setBuyerName(buyerName);
        result.setBuyerTaxNo(buyerTaxNo);
        result.setSellerName(sellerName);
        result.setSellerTaxNo(sellerTaxNo);
    }

    /** 是否明细表头行（其"名称"是列名，不能当购销方名称）。 */
    private boolean isDetailHeaderRow(String text) {
        String t = text.replaceAll("\\s+", "");
        return t.contains("货物或应税劳务") || t.contains("项目名称")
                || (t.contains("规格") && t.contains("数量") && t.contains("税率"));
    }

    private boolean isNameLabel(String label) {
        return "名称".equals(label);
    }

    private boolean isTaxNoLabel(String label) {
        return "纳税人识别号".equals(label) || "统一社会信用代码".equals(label)
                || "统一社会信用代码/纳税人识别号".equals(label);
    }

    /**
     * 标签感知切分：扫描文本中所有标签关键字（长标签优先，位置去重叠），
     * 每个标签的值 = 该标签到下一个标签（或行尾）之间。
     * <p>解决 PDF 分栏合并行串值：{@code 名称A 名称B} → [("名称",A), ("名称",B)]。</p>
     */
    private List<String[]> extractLabeledPairs(String text) {
        List<int[]> marks = new ArrayList<>();
        for (String label : LABELS) {
            int idx = 0;
            while ((idx = text.indexOf(label, idx)) >= 0) {
                boolean overlap = false;
                for (int[] m : marks) {
                    if (idx < m[0] + m[1] && idx + label.length() > m[0]) {
                        overlap = true;
                        break;
                    }
                }
                if (!overlap) {
                    marks.add(new int[]{idx, label.length()});
                }
                idx += label.length();
            }
        }
        marks.sort(Comparator.comparingInt(m -> m[0]));
        List<String[]> pairs = new ArrayList<>();
        for (int i = 0; i < marks.size(); i++) {
            int start = marks.get(i)[0];
            int end = start + marks.get(i)[1];
            int valueEnd = (i + 1 < marks.size()) ? marks.get(i + 1)[0] : text.length();
            String label = text.substring(start, end);
            String value = text.substring(end, valueEnd).replaceAll("^[\\s:：/、]+", "").trim();
            pairs.add(new String[]{label, value});
        }
        return pairs;
    }

    /**
     * 纳税人识别号国标校验与清洗（GB 32100-2015）：
     * 18 位统一社会信用代码（数字+大写字母，不含 I/O/S/V/Z）或 15 位数字旧税号。
     * 不合规时在值内提取合规子串；仍无返回 null（宁缺勿错）。
     */
    private String normalizeTaxNo(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.replaceAll("[\\s:：/、]", "");
        Matcher m18 = TAX_NO_18.matcher(s);
        if (m18.find()) {
            return m18.group();
        }
        Matcher m15 = TAX_NO_15.matcher(s);
        if (m15.find()) {
            return m15.group();
        }
        return null;
    }

    /**
     * 名称清洗：去残留标签词与空白（防串值残留），长度 2~60 校验；清洗后为空返回 null。
     */
    private String normalizeName(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.replaceAll("[\\s:：/、]+", "");
        for (String label : LABELS) {
            s = s.replace(label, "");
        }
        s = s.replaceAll("^[\\s:：/、]+|[\\s:：/、]+$", "").trim();
        if (s.length() < 2 || s.length() > 60) {
            return null;
        }
        return s;
    }

    // ---- 备注（可跨多行，位于价税合计行下方底部区域） ----

    /**
     * 备注提取（三种策略依次尝试）：
     * <ol>
     *   <li><b>横排</b>：行含"备注"标签 → 冒号后内容 + 向下收集；</li>
     *   <li><b>区间法</b>（横竖排通用）：数电票"备注"标签为竖排，文本层拆成"备"、"注"两个单字块，
     *       contains 永不匹配；改为取"价税合计"标签行与"开票人"行之间的区间内容，
     *       排除竖排单字标签行。</li>
     * </ol>
     */
    private void extractRemark(List<OcrLine> lines, InvoiceOcrResult result) {
        // 策略1：横排"备注"标签
        for (int i = 0; i < lines.size(); i++) {
            String t = lines.get(i).getText();
            if (!t.contains("备注")) {
                continue;
            }
            StringBuilder sb = new StringBuilder();
            String inline = afterColon(t, "备注");
            if (inline != null) {
                sb.append(inline);
            }
            for (int j = i + 1; j < lines.size(); j++) {
                String nt = lines.get(j).getText().trim();
                if (nt.isEmpty() || isRemarkStop(nt)) {
                    break;
                }
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(nt);
            }
            if (sb.length() > 0) {
                result.setRemark(sb.toString());
                return;
            }
        }
        // 策略2：区间法（竖排"备""注"标签的数电票版式）
        boolean hasVerticalRemarkLabel = false;
        int hi = -1;
        int lo = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            String t = lines.get(i).getText().trim();
            if (t.equals("备") || t.equals("注")) {
                hasVerticalRemarkLabel = true;
            }
            if (hi < 0 && t.contains("价税合计")) {
                hi = i;
            }
            if (hi >= 0 && t.contains("开票人")) {
                lo = i;
                break;
            }
        }
        if (hi < 0 || !hasVerticalRemarkLabel) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = hi + 1; i < lo; i++) {
            String t = lines.get(i).getText().trim();
            if (t.isEmpty() || t.equals("备") || t.equals("注")) {
                continue;
            }
            if (isRemarkStop(t)) {
                continue;
            }
            if (t.replaceAll("\\s+", "").equals("合计")) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(t);
        }
        if (sb.length() > 0) {
            result.setRemark(sb.toString());
        }
    }

    private boolean isRemarkStop(String text) {
        for (String kw : REMARK_STOP) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    // ---- 项目清单（明细行） ----

    /**
     * 抽取明细行：
     * <ol>
     *   <li>表头定位：含"货物或应税劳务"或同时含"规格"+"数量"的行；</li>
     *   <li>行分组：按 centerY 聚类（阈值=行高中位数×0.6），同一物理行的单元格聚合、按 x 排序；</li>
     *   <li>片段分配：含 % → 税率；数值从右到左 → 税额/金额/单价/数量；文本从左到右 → 名称/规格/单位；</li>
     *   <li>纯文本行视为上一项名称的换行延续；跳过"合计"与"价税合计"行。</li>
     * </ol>
     */
    private List<InvoiceOcrItem> extractItems(List<OcrLine> sortedLines) {
        List<InvoiceOcrItem> items = new ArrayList<>();
        // 1. 表头定位
        int headerIdx = -1;
        for (int i = 0; i < sortedLines.size(); i++) {
            if (isDetailHeaderRow(sortedLines.get(i).getText())) {
                headerIdx = i;
                break;
            }
        }
        if (headerIdx < 0) {
            return items;
        }
        // 2. 行分组（表头之后的行）
        double threshold = medianHeight(sortedLines) * 0.6;
        List<List<OcrLine>> rows = groupRows(sortedLines.subList(headerIdx + 1, sortedLines.size()), threshold);
        for (List<OcrLine> row : rows) {
            if (row.isEmpty()) {
                continue;
            }
            // 跳过合计/价税合计行
            String joined = joinRowText(row).replaceAll("\\s+", "");
            if (joined.contains("合计") || joined.contains("价税合计") || joined.contains("大写")) {
                break;
            }
            // 片段分类
            List<String> texts = new ArrayList<>();
            List<String> numerics = new ArrayList<>(); // 不含税率
            String taxRate = null;
            for (OcrLine seg : row) {
                String s = seg.getText().trim();
                if (s.isEmpty()) {
                    continue;
                }
                if (RATE_CELL.matcher(s).matches()) {
                    taxRate = s;
                } else if (NUMERIC_CELL.matcher(s).matches()) {
                    numerics.add(s);
                } else {
                    texts.add(s);
                }
            }
            // 纯文本行：上一项名称的换行延续
            if (numerics.isEmpty() && taxRate == null && !texts.isEmpty() && !items.isEmpty()) {
                InvoiceOcrItem last = items.get(items.size() - 1);
                String continuation = String.join("", texts);
                last.setName(last.getName() == null ? continuation : last.getName() + continuation);
                continue;
            }
            if (texts.isEmpty() && numerics.isEmpty() && taxRate == null) {
                continue;
            }
            // 构造明细项
            InvoiceOcrItem item = new InvoiceOcrItem();
            // 文本从左到右：名称 → 规格 → 单位
            if (!texts.isEmpty()) {
                item.setName(texts.get(0));
            }
            if (texts.size() > 1) {
                item.setSpec(texts.get(1));
            }
            if (texts.size() > 2) {
                item.setUnit(texts.get(2));
            }
            item.setTaxRate(taxRate);
            // 数值从右到左：税额 → 金额 → 单价 → 数量
            if (!numerics.isEmpty()) {
                item.setTaxAmount(clean(numerics.get(numerics.size() - 1)));
            }
            if (numerics.size() > 1) {
                item.setAmount(clean(numerics.get(numerics.size() - 2)));
            }
            if (numerics.size() > 2) {
                item.setUnitPrice(clean(numerics.get(numerics.size() - 3)));
            }
            if (numerics.size() > 3) {
                item.setQuantity(clean(numerics.get(numerics.size() - 4)));
            }
            items.add(item);
        }
        return items;
    }

    /** 按 centerY 聚类成物理行，行内按 x 升序。 */
    private List<List<OcrLine>> groupRows(List<OcrLine> lines, double threshold) {
        List<List<OcrLine>> rows = new ArrayList<>();
        List<OcrLine> current = new ArrayList<>();
        Double lastY = null;
        for (OcrLine l : lines) {
            if (lastY != null && Math.abs(l.getCenterY() - lastY) > threshold) {
                current.sort(Comparator.comparingDouble(this::centerX));
                rows.add(current);
                current = new ArrayList<>();
            }
            current.add(l);
            lastY = l.getCenterY();
        }
        if (!current.isEmpty()) {
            current.sort(Comparator.comparingDouble(this::centerX));
            rows.add(current);
        }
        return rows;
    }

    /** 行高中位数（用于行分组阈值）。 */
    private double medianHeight(List<OcrLine> lines) {
        double[] heights = new double[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            double[] box = lines.get(i).getBox();
            double minY = Math.min(Math.min(box[1], box[3]), Math.min(box[5], box[7]));
            double maxY = Math.max(Math.max(box[1], box[3]), Math.max(box[5], box[7]));
            heights[i] = Math.max(1.0, maxY - minY);
        }
        Arrays.sort(heights);
        return heights[heights.length / 2];
    }

    /** 片段水平中心（box[0]=左上x，box[4]=右下x）。 */
    private double centerX(OcrLine l) {
        double[] box = l.getBox();
        if (box == null || box.length < 8) {
            return 0;
        }
        return (box[0] + box[4]) / 2.0;
    }

    // ---- 通用提取工具 ----

    /** 提取首个金额数字部分（去 ¥ 与逗号）。 */
    private String extractMoney(String text) {
        Matcher m = MONEY.matcher(text);
        if (m.find()) {
            return m.group(1).replace(",", "").replace(" ", "");
        }
        return null;
    }

    /** 提取最后一个金额。 */
    private String lastMoney(String text) {
        List<String> all = allMonies(text);
        return all.isEmpty() ? null : all.get(all.size() - 1);
    }

    /** 提取全部金额。 */
    private List<String> allMonies(String text) {
        List<String> list = new ArrayList<>();
        Matcher m = MONEY.matcher(text);
        while (m.find()) {
            list.add(m.group(1).replace(",", "").replace(" ", ""));
        }
        return list;
    }

    /** 开票日期统一归一为 yyyy-MM-dd，避免回写时混入中文日期或斜杠格式。 */
    private String normalizeInvoiceDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String compact = raw.replaceAll("\\s+", "");
        Matcher matcher = Pattern.compile("(\\d{4})\\D+(\\d{1,2})\\D+(\\d{1,2})").matcher(compact);
        if (!matcher.find()) {
            return compact;
        }
        try {
            return LocalDate.of(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3))
            ).toString();
        } catch (DateTimeException | NumberFormatException e) {
            log.warn("发票日期标准化失败，保留原值: {}", compact, e);
            return compact;
        }
    }

    /** 数值单元格清洗：去 ¥ 与逗号（保留 % 由税率单独处理）。 */
    private String clean(String s) {
        return s.replace("¥", "").replace("￥", "").replace(",", "").trim();
    }

    /** 取关键字之后的值：去冒号与空白（仅用于备注等单标签场景；购销方走标签感知切分）。 */
    private String afterColon(String text, String keyword) {
        int idx = text.indexOf(keyword);
        if (idx < 0) {
            return null;
        }
        String tail = text.substring(idx + keyword.length()).replaceAll("[:：]", "").trim();
        return tail.isEmpty() ? null : tail;
    }

    private String joinRowText(List<OcrLine> row) {
        StringBuilder sb = new StringBuilder();
        for (OcrLine seg : row) {
            sb.append(seg.getText());
        }
        return sb.toString();
    }
}
