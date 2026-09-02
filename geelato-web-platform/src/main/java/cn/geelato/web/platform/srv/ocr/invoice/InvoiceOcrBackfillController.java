package cn.geelato.web.platform.srv.ocr.invoice;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.srv.ocr.invoice.entity.InvoiceOcrResult;
import cn.geelato.web.platform.srv.ocr.invoice.service.InvoiceOcrService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 批量发票核对（一次性工具）：对 il_invoice_req_file / il_payment_order_file 两表的
 * invoice_no（发票号码）、invoice_date（开票日期）、invoice_amount（发票金额）、remark（备注）
 * 逐行重新识别并比对，只标记差异、不修改数据库。
 *
 * <p>流程：查所有 file_id 非空的行（连同四字段当前值）→ {@link InvoiceOcrService#recognizeByFileId}
 * 识别（PDF 自动走文本层、图片走 OCR）→ 四字段逐一按类型归一化比对。</p>
 *
 * <p>比对规则：字段值相同则跳过；不同则记为一条异常，输出
 * {列名, 中文名, 当前值(dbValue), 解析值(ocrValue)}。全程不执行任何 UPDATE，可安全重跑。</p>
 *
 * @author geelato
 */
@ApiRestController(value = "/ocr/invoice")
@Slf4j
public class InvoiceOcrBackfillController {

    // ======== 一次性核对配置 ========
    /** 待核对的两张表。 */
    private static final List<String> TARGET_TABLES = List.of(
            "il_invoice_req_file",
            "il_payment_order_file");
    /**
     * 查询模板：file_id 为发票附件 id；取所有 file_id 非空的行，并带出四个受控字段的当前值。
     * 列使用别名（invoiceNo/invoiceDate/invoiceAmount/remark），取值走大小写不敏感的 {@link #pick}。
     */
    private static final String VERIFY_SQL_TEMPLATE =
            "SELECT id AS rowId, file_id AS fileId, invoice_no AS invoiceNo,"
                    + " invoice_date AS invoiceDate, invoice_amount AS invoiceAmount, remark"
                    + " FROM {table} WHERE file_id IS NOT NULL AND file_id <> ''";
    /** 日期归一化：从任意分隔的 yyyy-MM-dd 文本抽出年月日。 */
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{4})\\D+(\\d{1,2})\\D+(\\d{1,2})");
    // ===============================

    private final JdbcTemplate jdbcTemplate;
    private final InvoiceOcrService invoiceOcrService;

    @Autowired
    public InvoiceOcrBackfillController(@Qualifier("primaryJdbcTemplate") JdbcTemplate jdbcTemplate,
                                        InvoiceOcrService invoiceOcrService) {
        this.jdbcTemplate = jdbcTemplate;
        this.invoiceOcrService = invoiceOcrService;
    }

    /** 字段比对类型：决定归一化方式。 */
    private enum FieldType {
        /** 文本：首尾去空白后比对（发票号码、备注）。 */
        TEXT,
        /** 日期：统一归一为 yyyy-MM-dd 后比对。 */
        DATE,
        /** 金额：转 BigDecimal、按数值（忽略标度）比对。 */
        AMOUNT
    }

    /**
     * 执行核对（两张表依次处理）。逐条：解析发票 → 四字段归一化比对 → 收集差异（不改库）。
     *
     * @return {batchNo, results:[{table,total,matched,mismatched,failed,mismatches,failures}]}
     */
    @PostMapping("/backfill")
    public ApiResult<?> backfill() {
        String batchNo = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
        List<Map<String, Object>> tableResults = new ArrayList<>();
        for (String table : TARGET_TABLES) {
            tableResults.add(verifyTable(table));
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("batchNo", batchNo);
        summary.put("results", tableResults);
        return ApiResult.success(summary);
    }

    private Map<String, Object> verifyTable(String table) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                VERIFY_SQL_TEMPLATE.replace("{table}", table));
        List<Map<String, Object>> mismatches = new ArrayList<>();
        List<Map<String, String>> failures = new ArrayList<>();
        int matched = 0;
        for (Map<String, Object> row : rows) {
            Object rowId = pick(row, "rowId");
            Object fileId = pick(row, "fileId");
            if (rowId == null || fileId == null) {
                failures.add(failure(table, rowId, fileId, "结果缺少 rowId 或 fileId 列"));
                continue;
            }
            try {
                InvoiceOcrResult result = invoiceOcrService.recognizeByFileId(String.valueOf(fileId), false);
                List<Map<String, Object>> diffs = compareRow(result, row);
                if (diffs.isEmpty()) {
                    matched++;
                } else {
                    log.warn("发票字段差异 table={} rowId={} fileId={} 差异字段数={}",
                            table, rowId, fileId, diffs.size());
                    Map<String, Object> mismatch = new LinkedHashMap<>();
                    mismatch.put("table", table);
                    mismatch.put("rowId", String.valueOf(rowId));
                    mismatch.put("fileId", String.valueOf(fileId));
                    mismatch.put("diffs", diffs);
                    mismatches.add(mismatch);
                }
            } catch (Exception e) {
                log.error("发票识别失败 table={} rowId={} fileId={}", table, rowId, fileId, e);
                failures.add(failure(table, rowId, fileId,
                        e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            }
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("table", table);
        r.put("total", rows.size());
        r.put("matched", matched);
        r.put("mismatched", mismatches.size());
        r.put("failed", failures.size());
        r.put("mismatches", mismatches);
        r.put("failures", failures);
        return r;
    }

    // ---- 比对逻辑 ----

    /**
     * 对四个受控字段逐一比对，返回不一致的字段列表。
     * 每条差异含 {column, label, dbValue(当前值), ocrValue(解析值)}，值均为归一化后的可读形式。
     */
    private List<Map<String, Object>> compareRow(InvoiceOcrResult ocr, Map<String, Object> dbRow) {
        List<Map<String, Object>> diffs = new ArrayList<>();
        compareField(diffs, "invoice_no", "发票号码",
                pick(dbRow, "invoiceNo"), ocr.getInvoiceNumber(), FieldType.TEXT);
        compareField(diffs, "invoice_date", "开票日期",
                pick(dbRow, "invoiceDate"), ocr.getInvoiceDate(), FieldType.DATE);
        compareField(diffs, "invoice_amount", "发票金额",
                pick(dbRow, "invoiceAmount"), ocr.getTotalAmount(), FieldType.AMOUNT);
        compareField(diffs, "remark", "备注",
                pick(dbRow, "remark"), ocr.getRemark(), FieldType.TEXT);
        return diffs;
    }

    /** 单字段比对：按类型归一化后判等，不等则追加一条差异记录。 */
    private void compareField(List<Map<String, Object>> diffs, String column, String label,
                              Object dbRaw, Object ocrRaw, FieldType type) {
        String dbValue = canonical(dbRaw, type);
        String ocrValue = canonical(ocrRaw, type);
        if (Objects.equals(dbValue, ocrValue)) {
            return;
        }
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("column", column);
        diff.put("label", label);
        diff.put("dbValue", dbValue);
        diff.put("ocrValue", ocrValue);
        diffs.add(diff);
    }

    /** 按字段类型把值归一为可比对、可读的字符串（null 表示空值）。 */
    private String canonical(Object v, FieldType type) {
        if (v == null) {
            return null;
        }
        return switch (type) {
            case DATE -> canonicalDate(v);
            case AMOUNT -> canonicalAmount(v);
            case TEXT -> canonicalText(v);
        };
    }

    /** 文本归一：首尾去空白，空串视为 null。 */
    private String canonicalText(Object v) {
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    /**
     * 日期归一为 yyyy-MM-dd：兼容 JDBC 可能返回的 java.sql.Date/Timestamp、
     * LocalDate/LocalDateTime、java.util.Date，以及字符串（含中文/斜杠日期）。
     */
    private String canonicalDate(Object v) {
        if (v instanceof java.sql.Date d) {
            return d.toLocalDate().toString();
        }
        if (v instanceof java.sql.Timestamp t) {
            return t.toLocalDateTime().toLocalDate().toString();
        }
        if (v instanceof LocalDate ld) {
            return ld.toString();
        }
        if (v instanceof LocalDateTime ldt) {
            return ldt.toLocalDate().toString();
        }
        if (v instanceof java.util.Date d) {
            return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().toString();
        }
        return normalizeDateString(String.valueOf(v));
    }

    /** 从任意分隔的日期文本抽出 yyyy-MM-dd；无法解析时原样返回（比对时自然判不等）。 */
    private String normalizeDateString(String raw) {
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        Matcher m = DATE_PATTERN.matcher(s);
        if (m.find()) {
            try {
                return LocalDate.of(Integer.parseInt(m.group(1)),
                        Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))).toString();
            } catch (DateTimeException | NumberFormatException e) {
                log.warn("发票日期归一化失败，保留原值: {}", s);
            }
        }
        return s;
    }

    /**
     * 金额归一：转 BigDecimal 后去尾随零（忽略标度差异，如 100 与 100.00 视为相等）。
     * 兼容 BigDecimal、其它 Number，以及含 ¥/￥/逗号/空白的字符串；无法解析时原样返回。
     */
    private String canonicalAmount(Object v) {
        try {
            BigDecimal bd;
            if (v instanceof BigDecimal b) {
                bd = b;
            } else if (v instanceof Number n) {
                bd = new BigDecimal(n.toString());
            } else {
                String s = String.valueOf(v).replaceAll("[¥￥,\\s]", "");
                if (s.isEmpty()) {
                    return null;
                }
                bd = new BigDecimal(s);
            }
            return bd.stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            return canonicalText(v);
        }
    }

    // ---- 小工具 ----

    /** 大小写不敏感取列值。 */
    private Object pick(Map<String, Object> row, String key) {
        if (row.containsKey(key)) {
            return row.get(key);
        }
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey().equalsIgnoreCase(key)) {
                return e.getValue();
            }
        }
        return null;
    }

    private Map<String, String> failure(String table, Object rowId, Object fileId, String reason) {
        Map<String, String> f = new HashMap<>();
        f.put("table", table);
        f.put("rowId", rowId == null ? null : String.valueOf(rowId));
        f.put("fileId", fileId == null ? null : String.valueOf(fileId));
        f.put("reason", reason);
        return f;
    }
}
