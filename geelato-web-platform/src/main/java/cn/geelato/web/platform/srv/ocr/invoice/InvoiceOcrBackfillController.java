package cn.geelato.web.platform.srv.ocr.invoice;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.srv.ocr.invoice.entity.InvoiceOcrResult;
import cn.geelato.web.platform.srv.ocr.invoice.service.InvoiceOcrService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
 * 批量发票回填（一次性工具）。
 * <p>执行 {@link #BACKFILL_SQL} 得到 rowId（目标表主键值）与 fileId（发票附件 id），
 * 逐条调 {@link InvoiceOcrService} 解析后按 {@link #COLUMN_MAPPING} 参数化 UPDATE 写回目标表。</p>
 *
 * <p>留痕与回滚：每改一行前先把旧值/新值追加写入 {@link #LOG_FILE}（JSON Lines，
 * 每行一条）；{@code /rollback} 按日志把旧值（含 NULL）写回并打回滚标记，防重复回滚。</p>
 *
 * <p>一次性用完可整文件删除（日志文件亦可删），对现有识别链路零侵入。</p>
 *
 * @author geelato
 */
@ApiRestController(value = "/ocr/invoice")
@Slf4j
public class InvoiceOcrBackfillController {

    // ======== 一次性回填配置：按实际环境修改 ========
    /** 查询 SQL：结果必须含 rowId（目标表主键值）与 fileId（发票附件 id）两列别名。WHERE 自定幂等条件。 */
    private static final String BACKFILL_SQL =
            "SELECT id AS rowId, invoice_file_id AS fileId FROM expense_record WHERE invoice_no IS NULL";
    /** 写回的目标表名。 */
    private static final String TARGET_TABLE = "expense_record";
    /** 识别字段 → 目标列名（只写映射中且识别有值的字段）。 */
    private static final Map<String, String> COLUMN_MAPPING = Map.of(
            "invoiceNumber", "invoice_no",
            "invoiceDate", "invoice_date",
            "totalAmount", "invoice_amount",
            "buyerName", "buyer_name",
            "buyerTaxNo", "buyer_tax_no",
            "sellerName", "seller_name",
            "sellerTaxNo", "seller_tax_no");
    /** 回填日志文件（JSON Lines，相对工作目录）。 */
    private static final String LOG_FILE = "logs/ocr-backfill.jsonl";
    // ===============================================

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final InvoiceOcrService invoiceOcrService;

    @Autowired
    public InvoiceOcrBackfillController(JdbcTemplate jdbcTemplate, InvoiceOcrService invoiceOcrService) {
        this.jdbcTemplate = jdbcTemplate;
        this.invoiceOcrService = invoiceOcrService;
    }

    /**
     * 执行回填。逐条：解析发票 → 记日志（旧值落盘）→ UPDATE。
     *
     * @return {batchNo, total, success, failed, failures:[{rowId,fileId,reason}]}
     */
    @PostMapping("/backfill")
    public ApiResult<?> backfill() {
        String batchNo = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(BACKFILL_SQL);
        List<Map<String, String>> failures = new ArrayList<>();
        int success = 0;
        for (Map<String, Object> row : rows) {
            Object rowId = pick(row, "rowId");
            Object fileId = pick(row, "fileId");
            if (rowId == null || fileId == null) {
                failures.add(failure(rowId, fileId, "SQL 结果缺少 rowId 或 fileId 列"));
                continue;
            }
            try {
                InvoiceOcrResult result = invoiceOcrService.recognizeByFileId(String.valueOf(fileId), false);
                Map<String, Object> newValues = toColumnValues(result);
                if (newValues.isEmpty()) {
                    failures.add(failure(rowId, fileId, "未识别到任何可回填字段"));
                    continue;
                }
                // 旧值（仅将被修改的列）
                Map<String, Object> oldValues = queryOldValues(newValues.keySet(), rowId);
                // 先留痕（旧值落盘）再改库
                appendLog(batchNo, String.valueOf(rowId), String.valueOf(fileId), oldValues, newValues);
                int updated = executeUpdate(newValues, rowId);
                if (updated > 0) {
                    success++;
                } else {
                    failures.add(failure(rowId, fileId, "UPDATE 影响 0 行（主键 " + rowId + " 不存在？）"));
                }
            } catch (Exception e) {
                log.error("回填失败 rowId={} fileId={}", rowId, fileId, e);
                failures.add(failure(rowId, fileId, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("batchNo", batchNo);
        summary.put("total", rows.size());
        summary.put("success", success);
        summary.put("failed", failures.size());
        summary.put("failures", failures);
        return ApiResult.success(summary);
    }

    /**
     * 回滚：按日志把旧值（含 NULL）写回目标表。已回滚的记录跳过，防重复。
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
                    executeUpdate(oldValues, entry.get("rowId"));
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

    /** 识别结果 → 目标列值（仅映射中且识别有值的字段）。 */
    private Map<String, Object> toColumnValues(InvoiceOcrResult result) {
        Map<String, Object> values = new LinkedHashMap<>();
        putMapped(values, "invoiceNumber", result.getInvoiceNumber());
        putMapped(values, "invoiceDate", result.getInvoiceDate());
        putMapped(values, "totalAmount", result.getTotalAmount());
        putMapped(values, "remark", result.getRemark());
        putMapped(values, "buyerName", result.getBuyerName());
        putMapped(values, "buyerTaxNo", result.getBuyerTaxNo());
        putMapped(values, "sellerName", result.getSellerName());
        putMapped(values, "sellerTaxNo", result.getSellerTaxNo());
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

    /** 查询目标行旧值（仅指定列）。 */
    private Map<String, Object> queryOldValues(java.util.Set<String> columns, Object rowId) {
        String cols = String.join(", ", columns);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT " + cols + " FROM " + TARGET_TABLE + " WHERE id = ?", rowId);
        if (rows.isEmpty()) {
            throw new IllegalStateException("目标行不存在：" + rowId);
        }
        Map<String, Object> old = new LinkedHashMap<>();
        for (String col : columns) {
            old.put(col, pick(rows.get(0), col));
        }
        return old;
    }

    /** 参数化 UPDATE：SET 列=? ... WHERE id=?（值可为 null）。 */
    private int executeUpdate(Map<String, Object> values, Object rowId) {
        StringBuilder sql = new StringBuilder("UPDATE ").append(TARGET_TABLE).append(" SET ");
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

    private void appendLog(String batchNo, String rowId, String fileId,
                           Map<String, Object> oldValues, Map<String, Object> newValues) throws Exception {
        JSONObject entry = new JSONObject();
        entry.put("batchNo", batchNo);
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

    private Map<String, String> failure(Object rowId, Object fileId, String reason) {
        Map<String, String> f = new HashMap<>();
        f.put("rowId", rowId == null ? null : String.valueOf(rowId));
        f.put("fileId", fileId == null ? null : String.valueOf(fileId));
        f.put("reason", reason);
        return f;
    }
}
