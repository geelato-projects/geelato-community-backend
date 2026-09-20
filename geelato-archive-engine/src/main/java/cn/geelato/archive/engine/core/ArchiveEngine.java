package cn.geelato.archive.engine.core;

import cn.geelato.archive.config.ArchiveProperties;
import cn.geelato.archive.entity.ArchivePolicy;
import cn.geelato.archive.entity.ArchiveRun;
import cn.geelato.archive.enums.ExecutionModeEnum;
import cn.geelato.archive.enums.PolicyTypeEnum;
import cn.geelato.archive.enums.RunStatusEnum;
import cn.geelato.archive.exception.ArchiveException;
import cn.geelato.archive.service.ArchivePolicyService;
import cn.geelato.archive.service.ArchiveRunService;
import cn.geelato.archive.engine.channel.AutoModeProbe;
import cn.geelato.archive.engine.channel.MySqlArchiveChannel;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 归档引擎编排核心：门禁复核 → 目标结构核对 → 水位锁定 → id 游标分批循环
 * → 阶段1 写归档库（对账）→ 阶段2 删源库（对账）→ 进度/中止/收尾。
 *
 * <p><b>不误删保障</b>：任意时刻崩溃源库最多多数据、绝不丢数据；两阶段各自对账，
 * 任一不符硬失败中止（70003/70004），run 记录完整上下文（表/批次/游标/期望实际行数/堆栈）。</p>
 */
@Component
@Slf4j
public class ArchiveEngine {

    private static final int IN_CLAUSE_CHUNK = 200;
    private static final String SOURCE_DEFAULT_TIME_COLUMN = "create_at";
    private static final String SOURCE_DEL_STATUS_COLUMN = "del_status";

    private final cn.geelato.core.orm.Dao primaryDao;
    private final ArchivePolicyService policyService;
    private final ArchiveRunService runService;
    private final MySqlArchiveChannel channel;
    private final ArchiveProperties properties;
    private final org.springframework.transaction.support.TransactionTemplate sourceTxTemplate;
    private final Map<String, AtomicBoolean> cancelRequests = new ConcurrentHashMap<>();

    @Autowired
    public ArchiveEngine(@Qualifier("primaryDao") cn.geelato.core.orm.Dao primaryDao,
                         ArchivePolicyService policyService,
                         ArchiveRunService runService,
                         MySqlArchiveChannel channel,
                         ArchiveProperties properties) {
        this.primaryDao = primaryDao;
        this.policyService = policyService;
        this.runService = runService;
        this.channel = channel;
        this.properties = properties;
        this.sourceTxTemplate = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(
                        primaryDao.getJdbcTemplate().getDataSource()));
    }

    /** 请求中止：完成当前批后优雅停止（run 记为 canceled） */
    public boolean requestCancel(String policyId) {
        AtomicBoolean flag = cancelRequests.get(policyId);
        if (flag != null) {
            flag.set(true);
            return true;
        }
        return false;
    }

    // ==================== 主流程 ====================

    public ArchiveRun runPolicy(String policyId) {
        ArchivePolicy policy = policyService.get(policyId);
        policyService.revalidateBeforeRun(policy);
        if (runService.hasRunning(policyId)) {
            throw ArchiveException.policyRunningConflict("策略已有运行中的归档任务（policyId=" + policyId + "），同策略同时仅允许一个 run");
        }

        String resolvedTarget = policyService.resolveTargetTableName(policy);
        List<String> sourceColumns = channel.ensureTargetTable(policy, resolvedTarget);
        boolean hasDelStatus = sourceColumns.contains(SOURCE_DEL_STATUS_COLUMN);

        String mode = resolveExecutionMode(policy);
        Date watermark = lockWatermark(policy);

        ArchiveRun run = runService.startRun(policyId, watermark, mode);
        AtomicBoolean cancel = new AtomicBoolean(false);
        cancelRequests.put(policyId, cancel);
        Path batchDir = resolveBatchDir(run.getId());
        long begin = System.currentTimeMillis();
        long rows = 0;
        int batches = 0;
        String cursor = null;

        log.info("归档开始：policyId={}, table={} → {}, mode={}, watermark={}",
                policyId, policy.getTableName(), resolvedTarget, mode, watermark);
        try {
            int batchSize = policy.getBatchSize() == null ? properties.getDefaultBatchSize() : policy.getBatchSize();
            long interval = policy.getBatchIntervalMs() == null ? properties.getDefaultBatchIntervalMs() : policy.getBatchIntervalMs();
            long maxRows = policy.getMaxRowsPerRun() == null ? 0 : policy.getMaxRowsPerRun();
            boolean limitReached = false;

            while (!cancel.get()) {
                List<String> ids = selectIds(policy, watermark, cursor, batchSize, hasDelStatus);
                if (ids.isEmpty()) {
                    break;
                }
                if (maxRows > 0 && rows + ids.size() >= maxRows) {
                    // 渐进式归档：截断到单次上限，到量即收、次日继续
                    int remain = (int) (maxRows - rows);
                    if (remain <= 0) {
                        limitReached = true;
                        break;
                    }
                    ids = new ArrayList<>(ids.subList(0, remain));
                    limitReached = true;
                }

                // 阶段1：写归档库（内部对账，不符抛 70003）
                channel.migrateBatch(policy, resolvedTarget, sourceColumns, ids, mode, batchDir);
                // 阶段2：删源库（事务内对账，不符抛 70004 并回滚）
                deleteSourceRows(policy.getTableName(), ids, run, batches + 1, cursor);

                rows += ids.size();
                batches++;
                cursor = ids.get(ids.size() - 1);
                run.setArchivedRows(rows);
                run.setBatches(batches);
                run.setCursorId(cursor);
                runService.updateProgress(run);

                if (limitReached) {
                    break;
                }
                if (interval > 0) {
                    Thread.sleep(interval);
                }
            }

            long cost = System.currentTimeMillis() - begin;
            if (cancel.get()) {
                runService.finishCanceled(run, rows, batches, cost,
                        String.format("手动中止：已归档 %d 行 / %d 批", rows, batches));
                policyService.updateLastRun(policyId, run.getId(), RunStatusEnum.CANCELED.name());
            } else {
                String message = limitReached
                        ? String.format("达单次上限收尾（%d 行），未搬完的过期数据次日继续：已归档 %d 行 / %d 批", maxRows, rows, batches)
                        : String.format("归档完成：无剩余过期数据，共 %d 行 / %d 批", rows, batches);
                runService.finishSuccess(run, rows, batches, cost, message);
                policyService.updateLastRun(policyId, run.getId(), RunStatusEnum.SUCCESS.name());
            }
            log.info("归档结束：policyId={}, status={}, rows={}, batches={}, costMs={}",
                    policyId, run.getStatus(), rows, batches, cost);
            return run;
        } catch (ArchiveException e) {
            failRun(run, policy, e, batches + 1, cursor);
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failRun(run, policy, e, batches + 1, cursor);
            throw ArchiveException.validationError("归档执行被中断：" + e.getMessage(), e);
        } catch (Exception e) {
            failRun(run, policy, e, batches + 1, cursor);
            throw e;
        } finally {
            cancelRequests.remove(policyId);
            cleanupBatchDir(batchDir);
        }
    }

    // ==================== 阶段2：源库删除（事务 + 对账） ====================

    private void deleteSourceRows(String tableName, List<String> ids, ArchiveRun run, int batchNo, String cursor) {
        Long deleted = sourceTxTemplate.execute(status -> {
            long total = 0;
            for (List<String> chunk : partition(ids, IN_CLAUSE_CHUNK)) {
                String placeholders = String.join(", ", Collections.nCopies(chunk.size(), "?"));
                total += primaryDao.getJdbcTemplate().update(
                        "DELETE FROM " + tableName + " WHERE id IN (" + placeholders + ")", chunk.toArray());
            }
            return total;
        });
        if (deleted == null || deleted != ids.size()) {
            // 抛出即回滚本批删除（对账语义：归档库已完整，可安全重跑）
            throw ArchiveException.sourceDeleteMismatch(String.format(
                    "源表删除对账不一致：表 %s，本批期望删除 %d 行，实际删除 %d 行（整批已回滚，归档库数据完整，重跑安全）",
                    tableName, ids.size(), deleted == null ? -1 : deleted));
        }
    }

    // ==================== 选批（id 游标） ====================

    private List<String> selectIds(ArchivePolicy policy, Date watermark, String cursor,
                                   int batchSize, boolean hasDelStatus) {
        WhereClause where = buildSourceWhere(policy, watermark, hasDelStatus);
        String sql = "SELECT id FROM " + policy.getTableName() + " WHERE " + where.sql
                + " AND id > ? ORDER BY id ASC LIMIT " + batchSize;
        List<Object> params = new ArrayList<>(where.params);
        params.add(cursor);
        return primaryDao.getJdbcTemplate().queryForList(sql, params.toArray(), String.class);
    }

    /**
     * 策略条件构造（静态语义，可单测）：
     * DEFAULT → create_at &lt; watermark（参数化）；CUSTOM → (whereCondition) 原样拼接。
     * 条件仅参与"选 id"，搬移与删除始终按 id IN。
     */
    static WhereClause buildSourceWhere(ArchivePolicy policy, Date watermark, boolean hasDelStatus) {
        StringBuilder sql = new StringBuilder("(");
        List<Object> params = new ArrayList<>();
        if (PolicyTypeEnum.CUSTOM.name().equalsIgnoreCase(policy.getPolicyType())) {
            sql.append("(").append(policy.getWhereCondition()).append(")");
        } else {
            sql.append(SOURCE_DEFAULT_TIME_COLUMN).append(" < ?");
            params.add(watermark);
        }
        sql.append(")");
        if (hasDelStatus && !Boolean.TRUE.equals(policy.getIncludeDeleted())) {
            sql.append(" AND (").append(SOURCE_DEL_STATUS_COLUMN).append(" = 0 OR ")
                    .append(SOURCE_DEL_STATUS_COLUMN).append(" IS NULL)");
        }
        return new WhereClause(sql.toString(), params);
    }

    record WhereClause(String sql, List<Object> params) {
    }

    // ==================== 水位与模式 ====================

    /** DEFAULT 策略：run 开始时一次性锁定窗口边界，防长时间运行边界漂移 */
    private Date lockWatermark(ArchivePolicy policy) {
        if (!PolicyTypeEnum.DEFAULT.name().equalsIgnoreCase(policy.getPolicyType())
                || policy.getRetentionDays() == null) {
            return null;
        }
        return new Date(System.currentTimeMillis() - policy.getRetentionDays() * 86_400_000L);
    }

    private String resolveExecutionMode(ArchivePolicy policy) {
        String configured = policy.getExecutionMode() == null
                ? ExecutionModeEnum.AUTO.name() : policy.getExecutionMode();
        if (!ExecutionModeEnum.AUTO.name().equalsIgnoreCase(configured)) {
            return configured.toUpperCase();
        }
        String sourceUrl = dataSourceUrl(channel.sourceDataSource());
        String targetUrl;
        if (policy.getConnectId() == null || policy.getConnectId().isBlank()) {
            targetUrl = sourceUrl;
        } else {
            try (Connection connection = channel.resolveTargetConnection(policy.getConnectId())) {
                targetUrl = connection.getMetaData().getURL();
            } catch (SQLException e) {
                throw ArchiveException.validationError("归档库连接不可达：" + policy.getConnectId() + "，原因：" + e.getMessage(), e);
            }
        }
        ExecutionModeEnum probed = AutoModeProbe.probe(sourceUrl, targetUrl);
        log.info("AUTO 模式探测：source={}, target={} → {}", sourceUrl, targetUrl, probed);
        return probed.name();
    }

    // ==================== 回迁 ====================

    /**
     * 回迁（反向搬移）：按 id 列表把归档库数据搬回源表。
     * 两阶段对账：目标拉行→源清残留→源插入→源 count 对账→删目标→删除数对账。
     */
    public ArchiveRun restorePolicy(String policyId, List<String> ids) {
        ArchivePolicy policy = policyService.get(policyId);
        if (policy == null) {
            throw ArchiveException.validationError("归档策略不存在：" + policyId);
        }
        if (ids == null || ids.isEmpty()) {
            throw ArchiveException.validationError("回迁必须指定 id 列表");
        }
        String resolvedTarget = policyService.resolveTargetTableName(policy);
        List<String> sourceColumns = channel.ensureTargetTable(policy, resolvedTarget);

        ArchiveRun run = runService.startRun(policyId, null, "RESTORE");
        long begin = System.currentTimeMillis();
        try {
            String columnList = String.join(", ", sourceColumns);
            String placeholdersIn = String.join(", ", Collections.nCopies(ids.size(), "?"));
            String targetSelect = "SELECT " + columnList + " FROM " + resolvedTarget
                    + " WHERE id IN (" + placeholdersIn + ")";
            List<Object[]> rows = channel.targetJdbcForPolicy(policy).query(targetSelect, ids.toArray(),
                    rs -> {
                        int n = rs.getMetaData().getColumnCount();
                        List<Object[]> list = new ArrayList<>();
                        while (rs.next()) {
                            Object[] row = new Object[n];
                            for (int i = 0; i < n; i++) {
                                row[i] = rs.getObject(i + 1);
                            }
                            list.add(row);
                        }
                        return list;
                    });
            if (rows.size() != ids.size()) {
                throw ArchiveException.targetWriteMismatch(String.format(
                        "回迁读取对账不一致：归档表 %s 中期望 %d 行，实际 %d 行（源库未动）",
                        resolvedTarget, ids.size(), rows.size()));
            }
            // 源库：清残留 + 插入 + 对账（事务）
            String insertSql = "INSERT INTO " + policy.getTableName() + " (" + columnList + ") VALUES ("
                    + String.join(", ", Collections.nCopies(sourceColumns.size(), "?")) + ")";
            String deleteSql = "DELETE FROM " + policy.getTableName() + " WHERE id IN (" + placeholdersIn + ")";
            Integer restored = sourceTxTemplate.execute(status -> {
                primaryDao.getJdbcTemplate().update(deleteSql, ids.toArray());
                primaryDao.getJdbcTemplate().batchUpdate(insertSql, rows);
                return null;
            });
            Long sourceCount = countSourceByIds(policy.getTableName(), ids);
            if (sourceCount == null || sourceCount != ids.size()) {
                throw ArchiveException.targetWriteMismatch(String.format(
                        "回迁写入对账不一致：源表 %s 期望 %d 行，复核 %d 行", policy.getTableName(), ids.size(), sourceCount));
            }
            // 删归档侧 + 对账
            long targetDeleted = channel.deleteTargetByIds(policy, resolvedTarget, ids);
            if (targetDeleted != ids.size()) {
                throw ArchiveException.sourceDeleteMismatch(String.format(
                        "回迁清理对账不一致：归档表 %s 期望删除 %d 行，实际 %d 行（源表已恢复，残留可再次回迁）",
                        resolvedTarget, ids.size(), targetDeleted));
            }
            long cost = System.currentTimeMillis() - begin;
            runService.finishSuccess(run, ids.size(), 1, cost,
                    String.format("回迁完成：%d 行 %s → %s", ids.size(), resolvedTarget, policy.getTableName()));
            policyService.updateLastRun(policyId, run.getId(), RunStatusEnum.SUCCESS.name());
            return run;
        } catch (Exception e) {
            failRun(run, policy, e, 1, null);
            throw e instanceof ArchiveException ae ? ae
                    : new IllegalStateException("回迁失败：" + e.getMessage(), e);
        }
    }

    private Long countSourceByIds(String tableName, List<String> ids) {
        String sql = "SELECT COUNT(*) FROM " + tableName + " WHERE id IN ("
                + String.join(", ", Collections.nCopies(ids.size(), "?")) + ")";
        return primaryDao.getJdbcTemplate().queryForObject(sql, ids.toArray(), Long.class);
    }

    // ==================== 失败记录（精确诊断） ====================

    private void failRun(ArchiveRun run, ArchivePolicy policy, Exception e, int batchNo, String cursor) {
        JSONObject error = new JSONObject();
        error.put("policyId", policy.getId());
        error.put("table", policy.getTableName());
        error.put("targetTable", policyService.resolveTargetTableName(policy));
        error.put("batchNo", batchNo);
        error.put("cursor", cursor);
        if (e instanceof ArchiveException ae) {
            error.put("errorCode", ae.getErrorCode());
        }
        JSONObject exception = new JSONObject();
        exception.put("type", e.getClass().getName());
        exception.put("message", e.getMessage());
        StringBuilder stack = new StringBuilder();
        StackTraceElement[] elements = e.getStackTrace();
        for (int i = 0; i < Math.min(elements.length, 50); i++) {
            stack.append("    at ").append(elements[i]).append('\n');
        }
        exception.put("stack", stack.toString());
        error.put("exception", exception);
        runService.finishFailed(run, "归档失败：" + e.getMessage(), error.toJSONString());
        policyService.updateLastRun(policy.getId(), run.getId(), RunStatusEnum.FAILED.name());
        log.error("归档失败：policyId={}, table={}, batchNo={}, cursor={}",
                policy.getId(), policy.getTableName(), batchNo, cursor, e);
    }

    // ==================== 工具 ====================

    private Path resolveBatchDir(String runId) {
        String base = properties.getTempDir();
        Path dir = (base == null || base.isBlank())
                ? Path.of(System.getProperty("java.io.tmpdir"), "geelato-archive")
                : Path.of(base);
        return dir.resolve(runId);
    }

    private void cleanupBatchDir(Path batchDir) {
        if (Files.exists(batchDir)) {
            try (var stream = Files.list(batchDir)) {
                for (Path p : stream.toList()) {
                    Files.deleteIfExists(p);
                }
                Files.deleteIfExists(batchDir);
            } catch (IOException e) {
                log.warn("清理归档临时目录失败：{}", batchDir, e);
            }
        }
    }

    private String dataSourceUrl(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getURL();
        } catch (SQLException e) {
            throw ArchiveException.validationError("读取主库连接元数据失败：" + e.getMessage(), e);
        }
    }

    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> parts = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            parts.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return parts;
    }
}
