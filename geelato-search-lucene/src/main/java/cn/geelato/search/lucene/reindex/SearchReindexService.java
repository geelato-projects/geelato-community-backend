package cn.geelato.search.lucene.reindex;

import cn.geelato.search.api.SearchEngine;
import cn.geelato.search.lucene.config.GeelatoSearchProperties;
import cn.geelato.search.lucene.config.GeelatoSearchProperties.DomainConfig;
import cn.geelato.search.lucene.engine.LuceneSearchEngine;
import cn.geelato.search.lucene.sync.DocumentLoader;
import cn.geelato.search.lucene.sync.SearchDomainRegistry;
import cn.geelato.search.lucene.sync.SearchSyncService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 存量重建：主键游标分页扫描 → 回表聚合 → 批量 upsert。
 * 域级互斥（同一域不并发重建），重建期间检索持续可用（旧索引渐进覆盖），
 * 完成后写入 reindexed 水位——未标记的域不参与查询路由（防存量未入库时漏单）。
 *
 * <p>{@link #reindexAsync} 供管理端点触发：单线程池后台执行（百万行回表为分钟级，
 * HTTP 同步等待会超时），同域重复触发直接返回进行中进度，不排队堆积。
 */
@Slf4j
public class SearchReindexService {

    private final SearchDomainRegistry registry;
    private final DocumentLoader documentLoader;
    private final SearchEngine searchEngine;
    private final SearchSyncService syncService;
    private final GeelatoSearchProperties properties;
    private final Map<String, Object> domainLocks = new ConcurrentHashMap<>();

    /** 进行中/最近完成的重建进度（管理查询用）。 */
    private final Map<String, ReindexProgress> progresses = new ConcurrentHashMap<>();

    /** 异步重建执行器（单线程顺序执行，避免多域并行拖垮 DB 回表）。 */
    private final ExecutorService reindexExecutor = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "search-reindex"));

    /** 正在重建的域（重复触发去重）。 */
    private final Set<String> runningDomains = ConcurrentHashMap.newKeySet();

    public SearchReindexService(SearchDomainRegistry registry, DocumentLoader documentLoader,
                                SearchEngine searchEngine, SearchSyncService syncService,
                                GeelatoSearchProperties properties) {
        this.registry = registry;
        this.documentLoader = documentLoader;
        this.searchEngine = searchEngine;
        this.syncService = syncService;
        this.properties = properties;
    }

    /** 同步重建（编程调用/测试用）。 */
    public ReindexProgress reindex(String domainId) {
        requireDomain(domainId);
        Object lock = domainLocks.computeIfAbsent(domainId, k -> new Object());
        synchronized (lock) {
            return doReindexLocked(domainId);
        }
    }

    /**
     * 异步触发重建（管理端点用）：立即返回进度对象，后台执行并持续更新同一引用。
     * 同域已在重建中 → 直接返回当前进度，不重复执行；其他域的重建排队顺序执行。
     */
    public ReindexProgress reindexAsync(String domainId) {
        requireDomain(domainId);
        ReindexProgress progress = progresses.compute(domainId, (k, existing) ->
                existing == null || existing.isFinished() || existing.getError() != null
                        ? new ReindexProgress(k) : existing);
        if (!runningDomains.add(domainId)) {
            log.info("搜索域重建已在进行中，返回当前进度: domain={}", domainId);
            return progress;
        }
        reindexExecutor.submit(() -> {
            try {
                reindex(domainId);
            } catch (Exception ex) {
                log.error("异步重建失败: domain={}", domainId, ex);
            } finally {
                runningDomains.remove(domainId);
            }
        });
        return progress;
    }

    public ReindexProgress getProgress(String domainId) {
        return progresses.get(domainId);
    }

    /** 是否正在重建（域不存在返回 false）。 */
    public boolean isRunning(String domainId) {
        return runningDomains.contains(domainId);
    }

    /** 容器销毁时终止重建线程。 */
    public void shutdown() {
        reindexExecutor.shutdownNow();
    }

    private void requireDomain(String domainId) {
        DomainConfig domain = registry.byId(domainId);
        if (domain == null) {
            throw new IllegalArgumentException("搜索域不存在: " + domainId);
        }
    }

    /** 域锁内执行：复用/创建进度对象后游标重建（runningDomains 保证同域不并发）。 */
    private ReindexProgress doReindexLocked(String domainId) {
        DomainConfig domain = registry.byId(domainId);
        ReindexProgress progress = progresses.compute(domainId, (k, existing) ->
                existing == null || existing.isFinished() || existing.getError() != null
                        ? new ReindexProgress(k) : existing);
        return doReindex(domain, progress);
    }

    private ReindexProgress doReindex(DomainConfig domain, ReindexProgress progress) {
        int batchSize = properties.getReindexBatchSize();
        String cursor = null;
        try {
            while (true) {
                List<String> ids = documentLoader.scanIds(domain, cursor, batchSize);
                if (ids.isEmpty()) {
                    break;
                }
                // 批式回表：主行与子实体各一次 IN 批查，往返 O(批数) 而非 O(行数)
                int loaded = syncService.syncUpsertBatch(domain, ids);
                progress.done.addAndGet(loaded);
                progress.skipped.addAndGet(ids.size() - loaded);
                cursor = ids.get(ids.size() - 1);
                if (ids.size() < batchSize) {
                    break;
                }
            }
            ((LuceneSearchEngine) searchEngine).markReindexed(domain.getId());
            progress.finished = true;
            log.info("搜索域重建完成: domain={}, docs={}, skipped={}",
                    domain.getId(), progress.getDone(), progress.getSkipped());
            return progress;
        } catch (Exception ex) {
            progress.error = ex.getMessage();
            log.error("搜索域重建失败: domain={}", domain.getId(), ex);
            throw ex;
        }
    }

    @Getter
    public static class ReindexProgress {
        private final String domainId;
        private final AtomicLong done = new AtomicLong();
        private final AtomicLong skipped = new AtomicLong();
        private volatile boolean finished = false;
        private volatile String error;

        public ReindexProgress(String domainId) {
            this.domainId = domainId;
        }

        public boolean isFinished() {
            return finished;
        }

        public String getError() {
            return error;
        }
    }
}
