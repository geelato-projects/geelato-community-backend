package cn.geelato.web.platform.srv.ocr.invoice;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.srv.ocr.invoice.entity.InvoiceOcrResult;
import cn.geelato.web.platform.srv.ocr.invoice.service.InvoiceOcrService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 批量发票回填（一次性工具）：修复 il_invoice_req_file / il_payment_order_file 两表的
 * invoice_no（发票号码）、invoice_date（开票时间）、invoice_amount（发票金额）、remark（备注）。
 *
 * <p>流程：查待修复行（file_id）→ {@link InvoiceOcrService#recognizeByFileId} 识别
 * （PDF 自动走文本层、图片走 OCR）→ 先记日志再参数化 UPDATE。</p>
 *
 * <p>修复条件：四个字段均为空的行才处理（SQL WHERE 固定），可安全重跑。
 * 每行修改前把 {表名, id, fileId, 旧值, 新值} 追加写入日志文件（JSON Lines），
 * {@code /rollback} 按日志把旧值（含 NULL）写回并打回滚标记。</p>
 *
 * @author geelato
 */
@ApiRestController(value = "/ocr/invoice")
@Slf4j
public class InvoiceOcrBackfillController {

    // ======== 一次性回填配置 ========
    /** 待修复的两张表。 */
    private static final List<String> TARGET_TABLES = List.of(
            "il_invoice_req_file",
            "il_payment_order_file");
    /** 查询模板：file_id 为发票附件 id；仅四字段均为空的行才修复（幂等，可重跑）。 */
    private static final String BACKFILL_SQL_TEMPLATE =
            "SELECT id AS rowId, file_id AS fileId FROM {table}"
                    + " WHERE invoice_no IS NULL AND invoice_date IS NULL"
                    + " AND invoice_amount IS NULL AND remark IS NULL";
    /** 识别字段 → 目标列名。 */
    private static final Map<String, String> COLUMN_MAPPING = Map.of(
            "invoiceNumber", "invoice_no",
            "invoiceDate", "invoice_date",
            "totalAmount", "invoice_amount",
            "remark", "remark");
    /** 回填日志文件（JSON Lines，相对工作目录）。 */
    private static final String LOG_FILE = "logs/ocr-backfill.jsonl";
    // ===============================

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final InvoiceOcrService invoiceOcrService;

    @Autowired
    public InvoiceOcrBackfillController(@Qualifier("primaryJdbcTemplate") JdbcTemplate jdbcTemplate,
                                        InvoiceOcrService invoiceOcrService) {
        this.jdbcTemplate = jdbcTemplate;
        this.invoiceOcrService = invoiceOcrService;
    }

    /**
     * 执行回填（两张表依次处理）。逐条：解析发票 → 记日志（旧值落盘）→ UPDATE。
     *
     * @return {batchNo, results:[{table,total,success,failed,failures}]}
     */
    @PostMapping("/backfill")
    public ApiResult<?> backfill() {
        String batchNo = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
        List<Map<String, Object>> tableResults = new ArrayList<>();
        for (String table : TARGET_TABLES) {
            tableResults.add(backfillTable(batchNo, table));
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("batchNo", batchNo);
        summary.put("results", tableResults);
        return ApiResult.success(summary);
    }

    private Map<String, Object> backfillTable(String batchNo, String table) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                BACKFILL_SQL_TEMPLATE.replace("{table}", table));
        List<Map<String, String>> failures = new ArrayList<>();
        int success = 0;
        for (Map<String, Object> row : rows) {
            Object rowId = pick(row, "rowId");
            Object fileId = pick(row, "fileId");
            if (rowId == null || fileId == null) {
                failures.add(failure(table, rowId, fileId, "结果缺少 rowId 或 fileId 列"));
                continue;
            }
            try {
                InvoiceOcrResult result = invoiceOcrService.recognizeByFileId(String.valueOf(fileId), false);
                Map<String, Object> newValues = toColumnValues(result);
                if (newValues.isEmpty()) {
                    failures.add(failure(table, rowId, fileId, "未识别到任何可回填字段"));
                    continue;
                }
                Map<String, Object> oldValues = queryOldValues(table, newValues.keySet(), rowId);
                // 先留痕（表名+id+旧值落盘）再改库
                appendLog(batchNo, table, String.valueOf(rowId), String.valueOf(fileId), oldValues, newValues);
                int updated = executeUpdate(table, newValues, rowId);
                if (updated > 0) {
                    success++;
                } else {
                    failures.add(failure(table, rowId, fileId, "UPDATE 影响 0 行（主键 " + rowId + " 不存在？）"));
                }
            } catch (Exception e) {
                log.error("回填失败 table={} rowId={} fileId={}", table, rowId, fileId, e);
                failures.add(failure(table, rowId, fileId,
                        e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            }
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("table", table);
        r.put("total", rows.size());
        r.put("success", success);
        r.put("failed", failures.size());
        r.put("failures", failures);
        return r;
    }

    /**
     * 回滚：按日志把旧值（含 NULL）写回对应表。已回滚的记录跳过，防重复。
     *
     * @param batchNo 可选；缺省取最近一个未回滚批次
     * @return {batchNo, restored}
     */
    @PostMapping("/rollback")
    public ApiResult<?> rollback(@RequestParam(value = "batchNo", required = false) String batchNo) {
        try {
            List<JSONObject> entries = readLog();
            if (entries.isEmpty()) {
                return ApiResult.success(Map.of("restored", 0, "message", "日志为空，无可回滚记录"));
            }
            if (batchNo == null || batchNo.isBlank()) {
                for (int i = entries.size() - 1; i >= 0; i--) {
                    if (!entries.get(i).getBooleanValue("rolledBack")) {
                        batchNo = entries.get(i).getString("batchNo");
                        break;
                    }
                }
            }
            if (batchNo == null) {
                return ApiResult.success(Map.of("restored", 0, "message", "没有未回滚的批次"));
            }
            int restored = 0;
            String now = TS.format(LocalDateTime.now());
            for (JSONObject entry : entries) {
                if (!batchNo.equals(entry.getString("batchNo")) || entry.getBooleanValue("rolledBack")) {
                    continue;
                }
                JSONObject oldValues = entry.getJSONObject("oldValues");
                if (oldValues != null && !oldValues.isEmpty()) {
                    executeUpdate(entry.getString("tableName"), oldValues, entry.get("rowId"));
                }
                entry.put("rolledBack", true);
                entry.put("rollbackTime", now);
                restored++;
            }
            rewriteLog(entries);
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("batchNo", batchNo);
            summary.put("restored", restored);
            return ApiResult.success(summary);
        } catch (Exception e) {
            log.error("回滚失败", e);
            return ApiResult.fail("回滚失败：" + e.getMessage());
        }
    }

    // ---- 内部逻辑 ----

    /** 识别结果 → 目标列值（仅四字段中有值的）。 */
    private Map<String, Object> toColumnValues(InvoiceOcrResult result) {
        Map<String, Object> values = new LinkedHashMap<>();
        putMapped(values, "invoiceNumber", result.getInvoiceNumber());
        putMapped(values, "invoiceDate", result.getInvoiceDate());
        putMapped(values, "totalAmount", result.getTotalAmount());
        putMapped(values, "remark", result.getRemark());
        return values;
    }

    /** 识别字段有值且在映射中 → 写入目标列。 */
    private void putMapped(Map<String, Object> values, String field, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String column = COLUMN_MAPPING.get(field);
        if (column != null) {
            values.put(column, value);
        }
    }

    /** 查询目标行旧值（仅将被修改的列）。 */
    private Map<String, Object> queryOldValues(String table, java.util.Set<String> columns, Object rowId) {
        String cols = String.join(", ", columns);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT " + cols + " FROM " + table + " WHERE id = ?", rowId);
        if (rows.isEmpty()) {
            throw new IllegalStateException("目标行不存在：" + table + "#" + rowId);
        }
        Map<String, Object> old = new LinkedHashMap<>();
        for (String col : columns) {
            old.put(col, pick(rows.get(0), col));
        }
        return old;
    }

    /** 参数化 UPDATE：SET 列=? ... WHERE id=?（值可为 null）。 */
    private int executeUpdate(String table, Map<String, Object> values, Object rowId) {
        StringBuilder sql = new StringBuilder("UPDATE ").append(table).append(" SET ");
        List<Object> params = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, Object> e : values.entrySet()) {
            if (i++ > 0) {
                sql.append(", ");
            }
            sql.append(e.getKey()).append(" = ?");
            params.add(e.getValue());
        }
        sql.append(" WHERE id = ?");
        params.add(rowId);
        return jdbcTemplate.update(sql.toString(), params.toArray());
    }

    // ---- 日志文件（JSON Lines） ----

    private void appendLog(String batchNo, String table, String rowId, String fileId,
                           Map<String, Object> oldValues, Map<String, Object> newValues) throws Exception {
        JSONObject entry = new JSONObject();
        entry.put("batchNo", batchNo);
        entry.put("tableName", table);
        entry.put("rowId", rowId);
        entry.put("fileId", fileId);
        entry.put("time", TS.format(LocalDateTime.now()));
        entry.put("oldValues", oldValues);
        entry.put("newValues", newValues);
        entry.put("rolledBack", false);
        entry.put("rollbackTime", null);
        Path path = logPath();
        Files.write(path, (JSON.toJSONString(entry) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private List<JSONObject> readLog() throws Exception {
        Path path = logPath();
        if (!Files.exists(path)) {
            return new ArrayList<>();
        }
        List<JSONObject> entries = new ArrayList<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            entries.add(JSON.parseObject(line));
        }
        return entries;
    }

    private void rewriteLog(List<JSONObject> entries) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (JSONObject entry : entries) {
            sb.append(JSON.toJSONString(entry)).append(System.lineSeparator());
        }
        Files.write(logPath(), sb.toString().getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private Path logPath() throws Exception {
        Path path = Paths.get(LOG_FILE);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        return path;
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
