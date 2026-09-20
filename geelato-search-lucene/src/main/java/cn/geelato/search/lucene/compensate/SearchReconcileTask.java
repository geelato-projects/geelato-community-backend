package cn.geelato.search.lucene.compensate;

import cn.geelato.search.lucene.config.GeelatoSearchProperties;
import cn.geelato.search.lucene.config.GeelatoSearchProperties.DomainConfig;
import cn.geelato.search.lucene.sync.DocumentLoader;
import cn.geelato.search.lucene.sync.SearchDomainRegistry;
import cn.geelato.search.lucene.sync.SearchSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;/**
 * 对账任务（兜底一切绕过事件同步的写入：直连 SQL、DBA 变更、补偿遗漏）。
 *
 * <p>按 update_at 水位拉取域内主表与子表变更：主表变更主键、子表变更外键（DISTINCT），
 * 无条件回表重建（幂等，重写即修复——是"比对后重建"的超集，不漏写）。
 * 水位回拨 5 分钟重叠扫描，容忍边界时间。另做双端 count 比对，偏差仅告警不自动全量重建
 * （避免 DB 与索引双双异常时误判方向）。
 */
@Slf4j
public class SearchReconcileTask {

    /** 水位重叠回拨（分钟），容忍时钟与提交边界。 */
    private static final int WATERMARK_OVERLAP_MINUTES = 5;

    private final SearchDomainRegistry registry;
    private final SearchSyncService syncService;
    private final SearchSyncFailStore failStore;
    private final GeelatoSearchProperties properties;

    public SearchReconcileTask(SearchDomainRegistry registry, SearchSyncService syncService,
                               SearchSyncFailStore failStore, GeelatoSearchProperties properties) {
        this.registry = registry;
        this.syncService = syncService;
        this.failStore = failStore;
        this.properties = properties;
    }

    @Scheduled(cron = "${geelato.search.reconcile-cron:0 0 2 * * ?}")
    public void reconcile() {
        for (DomainConfig domain : registry.allDomains()) {
            try {
                reconcileDomain(domain);
            } catch (Exception ex) {
                log.error("对账任务失败: domain={}", domain.getId(), ex);
            }
        }
    }

    private void reconcileDomain(DomainConfig domain) {
        if (!syncService.getLuceneEngine().hasReindexed(domain.getId())) {
            log.info("域未完成 reindex，跳过对账: domain={}", domain.getId());
            return;
        }
        Timestamp watermark = Timestamp.valueOf(LocalDateTime.now().minusMinutes(WATERMARK_OVERLAP_MINUTES)
                .withSecond(0).withNano(0));
        Timestamp previous = failStore.getWatermark(domain.getId(), domain.getMainEntity());

        int rebuilt = 0;
        rebuilt += rebuildByWatermark(domain, domain.getMainEntity(), null, previous);
        for (DomainConfig.ChildConfig child : domain.getChildren()) {
            rebuilt += rebuildByWatermark(domain, child.getEntity(), child.getFkField(), previous);
        }
        failStore.saveWatermark(domain.getId(), domain.getMainEntity(), watermark);

        compareCount(domain);
        log.info("对账完成: domain={}, rebuiltDocs={}", domain.getId(), rebuilt);
    }

    /** 按水位扫描变更并重建（fkField 非空表示子实体，取外键去重后按主文档重建）；主键游标稳定分页 + 批式回表。 */
    private int rebuildByWatermark(DomainConfig domain, String entityName, String fkField, Timestamp watermark) {
        DocumentLoader loader = syncService.getDocumentLoader();
        int batchSize = properties.getReconcileBatchSize();
        java.sql.Timestamp scanFrom = watermark == null
                ? java.sql.Timestamp.valueOf(java.time.LocalDateTime.of(1970, 1, 1, 0, 0)) : watermark;
        int total = 0;
        String cursor = "";
        while (true) {
            List<String> ids = loader.scanIdsByUpdateAt(domain, entityName, fkField, scanFrom, cursor, batchSize);
            if (ids.isEmpty()) {
                break;
            }
            try {
                total += syncService.syncUpsertBatch(domain, ids);
            } catch (Exception ex) {
                log.warn("对账重建失败（留待下轮）: domain={}, ids={}", domain.getId(), ids.size(), ex);
            }
            cursor = ids.get(ids.size() - 1);
            if (ids.size() < batchSize) {
                break;
            }
        }
        return total;
    }

    /** 双端行数比对：偏差只告警（防双向异常时误修复）。 */
    private void compareCount(DomainConfig domain) {
        long dbCount = syncService.getDocumentLoader().countMainRows(domain);
        long indexCount = syncService.getLuceneEngine().countDocuments(domain.getId());
        if (dbCount != indexCount) {
            log.warn("对账行数不一致（可能存在补偿遗漏或并发窗口）: domain={}, db={}, index={}, diff={}",
                    domain.getId(), dbCount, indexCount, indexCount - dbCount);
        }
    }
}
