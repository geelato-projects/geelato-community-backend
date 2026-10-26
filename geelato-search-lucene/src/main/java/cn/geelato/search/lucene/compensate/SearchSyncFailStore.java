package cn.geelato.search.lucene.compensate;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 索引同步失败补偿队列（search_sync_fail 表）。
 *
 * <p>监听器全程 try-catch，任何失败（引擎异常、回表异常、行缺失竞态、主键缺失）
 * 落库为 PENDING 记录，由补偿任务按退避梯度重试；(domain_id, doc_id, op_type) 唯一，
 * 重复失败只更新不堆积。补偿记录写入本身失败（极端 DB 故障）只记 error 日志，
 * 由每日对账兜底——此时索引仍可服务，仅一致性收敛延迟。
 */
@Slf4j
public class SearchSyncFailStore {

    public static final String OP_UPSERT = "UPSERT";
    public static final String OP_DELETE = "DELETE";

    public static final String REASON_ENGINE = "ENGINE";
    public static final String REASON_READ_BACK = "READ_BACK";
    public static final String REASON_MISSING_ROW = "MISSING_ROW";
    public static final String REASON_MISSING_PK = "MISSING_PK";

    private final JdbcTemplate jdbcTemplate;

    /** 补偿/对账行 → 记录模型（字段全量映射，供 duePending 与运维视图共用）。 */
    private static final RowMapper<SyncFailRecord> RECORD_MAPPER = (rs, rowNum) -> {
        SyncFailRecord r = new SyncFailRecord();
        r.setId(rs.getString("id"));
        r.setDomainId(rs.getString("domain_id"));
        r.setDocId(rs.getString("doc_id"));
        r.setEntityName(rs.getString("entity_name"));
        r.setOpType(rs.getString("op_type"));
        r.setFailReason(rs.getString("fail_reason"));
        r.setErrorMessage(rs.getString("error_message"));
        r.setRetryCount(rs.getInt("retry_count"));
        r.setNextRetryAt(rs.getTimestamp("next_retry_at"));
        r.setStatus(rs.getString("status"));
        r.setCreateAt(rs.getTimestamp("create_at"));
        r.setUpdateAt(rs.getTimestamp("update_at"));
        return r;
    };

    public SearchSyncFailStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void initSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS search_sync_fail (
                  id VARCHAR(64) PRIMARY KEY,
                  domain_id VARCHAR(128) NOT NULL,
                  doc_id VARCHAR(128) NOT NULL,
                  entity_name VARCHAR(128) NOT NULL,
                  op_type VARCHAR(16) NOT NULL,
                  fail_reason VARCHAR(32) NOT NULL,
                  error_message VARCHAR(2000),
                  retry_count INT NOT NULL DEFAULT 0,
                  next_retry_at DATETIME,
                  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
                  create_at DATETIME,
                  update_at DATETIME,
                  UNIQUE KEY uk_sync_fail (domain_id, doc_id, op_type)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS search_sync_state (
                  domain_id VARCHAR(128) NOT NULL,
                  entity_name VARCHAR(128) NOT NULL,
                  watermark DATETIME,
                  update_at DATETIME,
                  PRIMARY KEY (domain_id, entity_name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
    }

    public void record(String domainId, String docId, String entityName, String opType,
                       String reason, Exception error) {
        String message = error == null ? null
                : error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
        if (message != null && message.length() > 2000) {
            message = message.substring(0, 2000);
        }
        String errorMessage = message;
        try {
            jdbcTemplate.update(
                    "INSERT INTO search_sync_fail (id, domain_id, doc_id, entity_name, op_type, fail_reason," +
                            " error_message, retry_count, next_retry_at, status, create_at, update_at)" +
                            " VALUES (?,?,?,?,?,?,?,0,?, 'PENDING', ?, ?)" +
                            " ON DUPLICATE KEY UPDATE fail_reason=VALUES(fail_reason), error_message=VALUES(error_message)," +
                            " next_retry_at=VALUES(next_retry_at), status='PENDING', update_at=VALUES(update_at)",
                    java.util.UUID.randomUUID().toString().replace("-", ""), domainId, docId, entityName,
                    opType, reason, errorMessage, Timestamp.valueOf(LocalDateTime.now().plusMinutes(1)),
                    Timestamp.valueOf(LocalDateTime.now()), Timestamp.valueOf(LocalDateTime.now()));
        } catch (DataAccessException ex) {
            // 补偿表本身不可写：error 告警，对账任务兜底
            log.error("记录索引同步失败时异常（由对账任务兜底）: domainId={}, docId={}, reason={}",
                    domainId, docId, reason, ex);
        }
    }

    /** 到期待重试记录（上限 batchSize）。 */
    public List<SyncFailRecord> duePending(int batchSize) {
        return jdbcTemplate.query(
                "SELECT * FROM search_sync_fail WHERE status='PENDING' AND next_retry_at <= ?" +
                        " ORDER BY next_retry_at LIMIT " + batchSize,
                RECORD_MAPPER, Timestamp.valueOf(LocalDateTime.now()));
    }

    /** 运维视图：按状态（可空=全部）取最近若干条同步失败/补偿记录，按更新时间倒序。 */
    public List<SyncFailRecord> listRecent(String status, int limit) {
        int n = Math.max(1, Math.min(limit, 500));
        if (status == null || status.isBlank()) {
            return jdbcTemplate.query(
                    "SELECT * FROM search_sync_fail ORDER BY update_at DESC LIMIT " + n, RECORD_MAPPER);
        }
        return jdbcTemplate.query(
                "SELECT * FROM search_sync_fail WHERE status = ? ORDER BY update_at DESC LIMIT " + n,
                RECORD_MAPPER, status);
    }

    /** 运维视图：各域对账水位（search_sync_state）。 */
    public List<Map<String, Object>> listWatermarks() {
        return jdbcTemplate.queryForList(
                "SELECT domain_id, entity_name, watermark, update_at FROM search_sync_state" +
                        " ORDER BY update_at DESC");
    }

    /** 运维视图：按域汇总补偿队列 PENDING/DEAD 计数。 */
    public Map<String, Map<String, Long>> domainFailSummary() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT domain_id, SUM(status = 'PENDING') AS pending, SUM(status = 'DEAD') AS dead" +
                        " FROM search_sync_fail GROUP BY domain_id");
        Map<String, Map<String, Long>> result = new java.util.LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Map<String, Long> counts = new java.util.LinkedHashMap<>();
            counts.put("pending", asLong(row.get("pending")));
            counts.put("dead", asLong(row.get("dead")));
            result.put(String.valueOf(row.get("domain_id")), counts);
        }
        return result;
    }

    private static long asLong(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    public void markSuccess(String id) {
        jdbcTemplate.update("DELETE FROM search_sync_fail WHERE id = ?", id);
    }

    /** 失败退避：count+1，按梯度推迟；超过 maxRetry 标记 DEAD（不再自动重试，需人工介入）。 */
    public void markFail(String id, int retryCount, int[] backoffMinutes, int maxRetry) {
        if (retryCount + 1 >= maxRetry) {
            jdbcTemplate.update("UPDATE search_sync_fail SET status='DEAD', retry_count=?, update_at=? WHERE id=?",
                    retryCount + 1, Timestamp.valueOf(LocalDateTime.now()), id);
            return;
        }
        int idx = Math.min(retryCount, backoffMinutes.length - 1);
        int delay = backoffMinutes[idx];
        jdbcTemplate.update("UPDATE search_sync_fail SET retry_count=?, next_retry_at=?, update_at=? WHERE id=?",
                retryCount + 1, Timestamp.valueOf(LocalDateTime.now().plusMinutes(delay)),
                Timestamp.valueOf(LocalDateTime.now()), id);
    }

    public int countByStatus(String status) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM search_sync_fail WHERE status=?", Integer.class, status);
        return count == null ? 0 : count;
    }

    /** 水位读写（对账任务）。 */
    public Timestamp getWatermark(String domainId, String entityName) {
        List<Timestamp> list = jdbcTemplate.queryForList(
                "SELECT watermark FROM search_sync_state WHERE domain_id=? AND entity_name=?",
                Timestamp.class, domainId, entityName);
        return list.isEmpty() ? null : list.get(0);
    }

    public void saveWatermark(String domainId, String entityName, Timestamp watermark) {
        jdbcTemplate.update(
                "INSERT INTO search_sync_state (domain_id, entity_name, watermark, update_at) VALUES (?,?,?,?)" +
                        " ON DUPLICATE KEY UPDATE watermark=VALUES(watermark), update_at=VALUES(update_at)",
                domainId, entityName, watermark, Timestamp.valueOf(LocalDateTime.now()));
    }

    @lombok.Getter
    @lombok.Setter
    public static class SyncFailRecord {
        private String id;
        private String domainId;
        private String docId;
        private String entityName;
        private String opType;
        private String failReason;
        private String errorMessage;
        private int retryCount;
        private Timestamp nextRetryAt;
        private String status;
        private Timestamp createAt;
        private Timestamp updateAt;
    }
}
