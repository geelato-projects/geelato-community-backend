package cn.geelato.exception.engine.scan;

import cn.geelato.core.orm.Dao;
import cn.geelato.exception.engine.core.Hit;
import cn.geelato.exception.engine.core.PipelineEngine;
import cn.geelato.exception.engine.core.PipelineOutcome;
import cn.geelato.exception.engine.core.TriggerMode;
import cn.geelato.exception.engine.hit.HitPolicies;
import cn.geelato.exception.engine.hit.HitSubmitService;
import cn.geelato.exception.engine.hit.SubmitReport;
import cn.geelato.exception.engine.lifecycle.InstanceLifecycleService;
import cn.geelato.exception.engine.support.EngineContextExecutor;
import cn.geelato.exception.entity.FmsExceptionRule;
import cn.geelato.exception.entity.FmsExceptionScanLog;
import cn.geelato.orm.MetaFactory;
import cn.geelato.orm.query.Filter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * 单规则一轮扫描执行：规则行 CAS 抢占（集群防重，stale 内嵌回收）→ 重建租户上下文 →
 * 管道 SCAN 全量 → 命中提交链 → diff 恢复（上轮命中本轮消失的 source_key 组级 AUTO_RECOVER）→
 * scan_log 全量回写 → finally 释放。单规则失败仅留痕不阻断调度。
 */
@Slf4j
public class ScanRunner {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Dao dynamicDao;
    private final PipelineEngine pipelineEngine;
    private final HitSubmitService hitSubmitService;
    private final InstanceLifecycleService lifecycle;
    private final int lockStaleMinutes;
    private final String runnerId;
    private final ExecutorService scanPool;

    public ScanRunner(@Qualifier("dynamicDao") Dao dynamicDao,
                      PipelineEngine pipelineEngine,
                      HitSubmitService hitSubmitService,
                      InstanceLifecycleService lifecycle,
                      int lockStaleMinutes,
                      int threadPoolSize) {
        this.dynamicDao = dynamicDao;
        this.pipelineEngine = pipelineEngine;
        this.hitSubmitService = hitSubmitService;
        this.lifecycle = lifecycle;
        this.lockStaleMinutes = lockStaleMinutes;
        this.runnerId = resolveRunnerId();
        this.scanPool = Executors.newFixedThreadPool(Math.max(1, threadPoolSize), r -> {
            Thread t = new Thread(r, "exception-scan-runner");
            t.setDaemon(true);
            return t;
        });
    }

    /** 异步提交一轮扫描（调度器入口）；池满直接丢弃留痕（下一 tick 兜底）。 */
    public boolean submitAsync(FmsExceptionRule rule) {
        try {
            scanPool.submit(() -> safeRunOneScan(rule));
            return true;
        } catch (RejectedExecutionException e) {
            log.warn("扫描池满，本轮跳过 ruleCode={}（下一 tick 兜底）", rule.getRuleCode());
            return false;
        }
    }

    /** 同步执行一轮扫描（adminScan 手动触发入口）；强制重建引擎系统身份，不沿用触发者会话。 */
    public Map<String, Object> runOneScanSync(FmsExceptionRule rule) {
        return EngineContextExecutor.withEngineContext(rule.getTenantCode(), () -> safeRunOneScan(rule));
    }

    @PreDestroy
    public void shutdown() {
        scanPool.shutdownNow();
    }

    private Map<String, Object> safeRunOneScan(FmsExceptionRule rule) {
        try {
            return runOneScan(rule);
        } catch (Exception e) {
            log.error("扫描执行异常 ruleCode={}: {}", rule.getRuleCode(), e.getMessage(), e);
            writeScanLog(rule, new Date(), new Date(), null, null, 0,
                    "扫描执行异常: " + e.getMessage(), "SCAN");
            releaseLock(rule.getId());
            return Map.of("ruleCode", rule.getRuleCode(), "status", "ERROR", "error", String.valueOf(e.getMessage()));
        }
    }

    private Map<String, Object> runOneScan(FmsExceptionRule rule) {
        Date startAt = new Date();
        if (!tryLock(rule.getId(), startAt)) {
            log.info("扫描跳过 ruleCode={}（锁被占用或规则已停用）", rule.getRuleCode());
            return Map.of("ruleCode", rule.getRuleCode(), "status", "SKIPPED_LOCKED");
        }
        try {
            return EngineContextExecutor.withContext(rule.getTenantCode(), () -> {
                log.info("扫描开始 ruleCode={} version={}", rule.getRuleCode(), rule.getVersion());
                PipelineOutcome outcome = pipelineEngine.execute(rule, TriggerMode.SCAN, null);
                SubmitReport report = new SubmitReport();
                int recovered = 0;
                if ("SUCCESS".equals(outcome.getStatus())) {
                    report = hitSubmitService.submit(rule, outcome.getValidHits());
                    recovered = recoverMissingSources(rule, outcome.getValidHits());
                }
                Date finishAt = new Date();
                writeScanLog(rule, startAt, finishAt, outcome, report, recovered,
                        "SUCCESS".equals(outcome.getStatus()) ? null : outcome.getError(), "SCAN");
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("ruleCode", rule.getRuleCode());
                result.put("status", outcome.getStatus());
                result.put("candidateCount", outcome.getCandidateCount());
                result.put("hitCount", outcome.getValidHits() == null ? 0 : outcome.getValidHits().size());
                result.put("instanceCreated", report.getInstanceCreated());
                result.put("instanceRenewed", report.getInstanceRenewed());
                result.put("instanceRecovered", recovered);
                result.put("suppressedCount", report.getSuppressedCount());
                result.put("costMs", outcome.getCostMs());
                result.put("engineVersion", outcome.getEngineVersion());
                log.info("扫描结束 ruleCode={} status={} candidateCount={} hitCount={} created={} renewed={} recovered={} suppressed={} costMs={}",
                        rule.getRuleCode(), outcome.getStatus(), outcome.getCandidateCount(),
                        result.get("hitCount"), report.getInstanceCreated(), report.getInstanceRenewed(),
                        recovered, report.getSuppressedCount(), outcome.getCostMs());
                if (!"SUCCESS".equals(outcome.getStatus())) {
                    log.warn("扫描失败 ruleCode={} status={} error={}", rule.getRuleCode(),
                            outcome.getStatus(), outcome.getError());
                }
                return result;
            });
        } finally {
            releaseLock(rule.getId());
        }
    }

    /** diff 恢复：规则在途 source_key 中，本轮命中集合未覆盖的组级关闭。 */
    private int recoverMissingSources(FmsExceptionRule rule, List<Hit> hits) {
        Set<String> currentKeys = HitPolicies.distinctSourceKeys(rule.getRuleCode(), hits);
        Set<String> activeKeys = activeSourceKeysOf(rule.getRuleCode());
        int recovered = 0;
        for (String sourceKey : activeKeys) {
            if (!currentKeys.contains(sourceKey)) {
                try {
                    int closed = lifecycle.recoverSourceGroup(rule.getRuleCode(), sourceKey, "扫描 diff：本轮未命中");
                    if (closed > 0) {
                        log.info("组级恢复 ruleCode={} sourceKey={} closed={}", rule.getRuleCode(), sourceKey, closed);
                    }
                    recovered += closed;
                } catch (Exception e) {
                    log.warn("组级恢复失败 ruleCode={} sourceKey={}: {}", rule.getRuleCode(), sourceKey, e.getMessage());
                }
            }
        }
        return recovered;
    }

    private Set<String> activeSourceKeysOf(String ruleCode) {
        List<Map<String, Object>> rows = dynamicDao.nativeQueryForMapList(
                "SELECT DISTINCT source_key FROM platform_exception WHERE rule_code = ? AND del_status = 0" +
                        " AND status IN ('OPEN','PROCESSING')", new Object[]{ruleCode});
        Set<String> keys = new LinkedHashSet<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Object key = row.get("source_key");
                if (key != null && !String.valueOf(key).isBlank()) {
                    keys.add(String.valueOf(key));
                }
            }
        }
        return keys;
    }

    /** CAS 抢占：空闲或 stale（执行超 lockStaleMinutes 视为死锁）才可抢到。 */
    private boolean tryLock(String ruleId, Date now) {
        Date staleBefore = new Date(now.getTime() - lockStaleMinutes * 60_000L);
        int updated = dynamicDao.nativeExecute(
                "UPDATE platform_exception_rule SET scan_status = 1, scan_started_at = ?, scan_runner = ?" +
                        " WHERE id = ? AND enable_status = 1 AND del_status = 0" +
                        " AND (scan_status = 0 OR scan_started_at IS NULL OR scan_started_at < ?)",
                new Object[]{now, runnerId, ruleId, staleBefore});
        return updated > 0;
    }

    private void releaseLock(String ruleId) {
        try {
            dynamicDao.nativeExecute(
                    "UPDATE platform_exception_rule SET scan_status = 0 WHERE id = ? AND scan_runner = ?",
                    new Object[]{ruleId, runnerId});
        } catch (Exception e) {
            log.warn("释放扫描锁失败 ruleId={}: {}（stale 兜底回收）", ruleId, e.getMessage());
        }
    }

    private void writeScanLog(FmsExceptionRule rule, Date startAt, Date finishAt,
                              PipelineOutcome outcome, SubmitReport report, int recovered,
                              String error, String triggerType) {
        try {
            FmsExceptionScanLog logRow = new FmsExceptionScanLog();
            logRow.setRuleCode(rule.getRuleCode());
            logRow.setTriggerType(triggerType);
            logRow.setStartAt(startAt);
            logRow.setFinishAt(finishAt);
            logRow.setCandidateCount(outcome == null ? 0 : (int) outcome.getCandidateCount());
            logRow.setHitCount(outcome == null || outcome.getValidHits() == null ? 0 : outcome.getValidHits().size());
            logRow.setInstanceCreated(report == null ? 0 : report.getInstanceCreated());
            logRow.setInstanceRenewed(report == null ? 0 : report.getInstanceRenewed());
            logRow.setInstanceRecovered(recovered);
            logRow.setSuppressedCount(report == null ? 0 : report.getSuppressedCount());
            logRow.setCostMs((int) Math.min(Integer.MAX_VALUE, finishAt.getTime() - startAt.getTime()));
            logRow.setStepsJson(outcome == null ? null : writeJson(outcome.getSteps()));
            logRow.setError(error);
            logRow.setTenantCode(rule.getTenantCode());
            logRow.setDelStatus(0);
            logRow.setCreateAt(finishAt);
            logRow.setUpdateAt(finishAt);
            logRow.setCreator(EngineContextExecutor.SYSTEM_USER_ID);
            logRow.setCreatorName(EngineContextExecutor.SYSTEM_USER_NAME);
            MetaFactory.save(logRow);
        } catch (Exception e) {
            log.error("scan_log 回写失败 ruleCode={}: {}", rule.getRuleCode(), e.getMessage());
        }
    }

    private static String writeJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private static String resolveRunnerId() {
        try {
            return InetAddress.getLocalHost().getHostName() + "@" + ProcessHandle.current().pid();
        } catch (Exception e) {
            return "unknown-runner";
        }
    }
}
