package cn.geelato.search.lucene.web;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.search.lucene.compensate.SearchSyncFailStore;
import cn.geelato.search.lucene.config.GeelatoSearchProperties.DomainConfig;
import cn.geelato.search.lucene.engine.LuceneSearchEngine;
import cn.geelato.search.lucene.reindex.SearchReindexService;
import cn.geelato.search.lucene.sync.DocumentLoader;
import cn.geelato.search.lucene.sync.SearchDomainRegistry;
import cn.geelato.web.common.annotation.ApiRuntimeRestController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 检索运维检查数据接口（自动加 /api 前缀 → /api/search-ops/**）。
 *
 * <p>只读诊断，供 {@code search-ops.html} 运维页消费：域一致性体检（索引数量 vs 主表行数、
 * 结构可解析性）、补偿队列明细、对账水位。存量重建 / 进度 / 调试等写操作仍复用
 * {@link SearchManageController} 的 {@code /api/search/*} 端点。
 *
 * <p>设计对齐 geelato-meta-sync（PageController + ApiController）先例；本控制器不含引擎写路径，
 * 换实现（如 ES）时仅需提供等价只读口径。
 */
@Slf4j
@ApiRuntimeRestController("/search-ops")
public class SearchOpsApiController {

    private static final String JSON_UTF8 = "application/json;charset=UTF-8";

    private final SearchDomainRegistry registry;
    private final LuceneSearchEngine engine;
    private final DocumentLoader documentLoader;
    private final SearchSyncFailStore failStore;
    private final SearchReindexService reindexService;

    public SearchOpsApiController(SearchDomainRegistry registry, LuceneSearchEngine engine,
                                  DocumentLoader documentLoader, SearchSyncFailStore failStore,
                                  SearchReindexService reindexService) {
        this.registry = registry;
        this.engine = engine;
        this.documentLoader = documentLoader;
        this.failStore = failStore;
        this.reindexService = reindexService;
    }

    /**
     * GET /api/search-ops/consistency — 全域一致性体检汇总。
     * <p>逐域给出：是否已 reindex、是否重建中、主表行数、索引文档数、行数是否对齐、
     * 补偿队列 PENDING/DEAD、结构体检问题。count 比对与运维口径一致（均不滤软删）。
     */
    @GetMapping(value = "/consistency", produces = JSON_UTF8)
    public ApiResult<Map<String, Object>> consistency() {
        Map<String, Map<String, Long>> queueByDomain = safeQueueSummary();
        List<Map<String, Object>> items = new ArrayList<>();
        int reindexedCount = 0;
        int consistentCount = 0;
        long pendingTotal = 0;
        long deadTotal = 0;
        for (DomainConfig domain : registry.allDomains()) {
            Map<String, Object> item = new LinkedHashMap<>();
            String domainId = domain.getId();
            item.put("domainId", domainId);
            item.put("mainEntity", domain.getMainEntity());

            boolean reindexed = engine.hasReindexed(domainId);
            item.put("reindexed", reindexed);
            item.put("reindexRunning", reindexService.isRunning(domainId));

            List<String> issues = new ArrayList<>(registry.issues(domain));
            long dbRows = -1;
            try {
                dbRows = documentLoader.countMainRows(domain);
            } catch (Exception ex) {
                issues.add("主表行数统计失败（回表异常）: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
            item.put("dbRows", dbRows < 0 ? null : dbRows);

            long indexDocs = reindexed ? safeCountDocuments(domainId, issues) : 0;
            item.put("indexDocs", indexDocs);

            boolean countMatch = reindexed && dbRows >= 0 && dbRows == indexDocs;
            item.put("countMatch", countMatch);
            if (!reindexed) {
                issues.add("域未完成存量重建（不参与检索路由）");
            } else if (dbRows >= 0 && !countMatch) {
                issues.add("行数不一致（可能存在遗漏同步或并发窗口）: db=" + dbRows + " index=" + indexDocs);
            }

            Map<String, Long> queue = queueByDomain.getOrDefault(domainId, Map.of());
            long pending = queue.getOrDefault("pending", 0L);
            long dead = queue.getOrDefault("dead", 0L);
            item.put("pending", pending);
            item.put("dead", dead);
            item.put("issues", issues);

            if (reindexed) {
                reindexedCount++;
            }
            if (reindexed && countMatch && issues.isEmpty()) {
                consistentCount++;
            }
            pendingTotal += pending;
            deadTotal += dead;
            items.add(item);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("domains", items.size());
        summary.put("reindexed", reindexedCount);
        summary.put("consistent", consistentCount);
        summary.put("inconsistent", items.size() - consistentCount);
        summary.put("pendingTotal", pendingTotal);
        summary.put("deadTotal", deadTotal);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("items", items);
        return ApiResult.success(result);
    }

    /**
     * GET /api/search-ops/compensation?status=PENDING|DEAD&limit=100 — 补偿队列明细。
     * <p>status 留空返回全部；limit 上限由存储层钳制。含失败原因、重试次数、下次重试时间、错误信息。
     */
    @GetMapping(value = "/compensation", produces = JSON_UTF8)
    public ApiResult<Map<String, Object>> compensation(
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "100") int limit) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (SearchSyncFailStore.SyncFailRecord r : failStore.listRecent(status, limit)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("domainId", r.getDomainId());
            m.put("docId", r.getDocId());
            m.put("entityName", r.getEntityName());
            m.put("opType", r.getOpType());
            m.put("failReason", r.getFailReason());
            m.put("status", r.getStatus());
            m.put("retryCount", r.getRetryCount());
            m.put("nextRetryAt", r.getNextRetryAt());
            m.put("updateAt", r.getUpdateAt());
            m.put("createAt", r.getCreateAt());
            m.put("errorMessage", r.getErrorMessage());
            items.add(m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("pending", failStore.countByStatus("PENDING"));
        result.put("dead", failStore.countByStatus("DEAD"));
        return ApiResult.success(result);
    }

    /** GET /api/search-ops/watermarks — 各域对账水位（最近一次对账落库的水位与更新时间）。 */
    @GetMapping(value = "/watermarks", produces = JSON_UTF8)
    public ApiResult<Map<String, Object>> watermarks() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", failStore.listWatermarks());
        return ApiResult.success(result);
    }

    // ===== 内部：诊断路径全程容错，单域异常不影响整体报告 =====

    private Map<String, Map<String, Long>> safeQueueSummary() {
        try {
            return failStore.domainFailSummary();
        } catch (Exception ex) {
            log.warn("补偿队列按域汇总失败（一致性报告降级为空计数）", ex);
            return Map.of();
        }
    }

    private long safeCountDocuments(String domainId, List<String> issues) {
        try {
            return engine.countDocuments(domainId);
        } catch (Exception ex) {
            issues.add("索引文档计数失败: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return -1;
        }
    }
}
