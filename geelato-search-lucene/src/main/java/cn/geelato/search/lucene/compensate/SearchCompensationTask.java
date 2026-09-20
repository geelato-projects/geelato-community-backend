package cn.geelato.search.lucene.compensate;

import cn.geelato.search.lucene.config.GeelatoSearchProperties;
import cn.geelato.search.lucene.config.GeelatoSearchProperties.DomainConfig;
import cn.geelato.search.lucene.sync.SearchDomainRegistry;
import cn.geelato.search.lucene.sync.SearchSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

/**
 * 索引同步补偿任务：扫 PENDING 且到期记录，回表重建/删除；
 * 成功即出队，失败按退避梯度（默认 1m/5m/30m/2h）推迟，超过上限标记 DEAD 并告警。
 */
@Slf4j
public class SearchCompensationTask {

    private static final int BATCH_SIZE = 100;

    private final SearchDomainRegistry registry;
    private final SearchSyncService syncService;
    private final SearchSyncFailStore failStore;
    private final GeelatoSearchProperties properties;

    public SearchCompensationTask(SearchDomainRegistry registry, SearchSyncService syncService,
                                  SearchSyncFailStore failStore, GeelatoSearchProperties properties) {
        this.registry = registry;
        this.syncService = syncService;
        this.failStore = failStore;
        this.properties = properties;
    }

    @Scheduled(cron = "${geelato.search.compensation-cron:0 * * * * ?}")
    public void compensate() {
        if (!registry.hasDomains()) {
            return;
        }
        List<SearchSyncFailStore.SyncFailRecord> records;
        try {
            records = failStore.duePending(BATCH_SIZE);
        } catch (Exception ex) {
            log.warn("补偿队列读取失败", ex);
            return;
        }
        if (records.isEmpty()) {
            return;
        }
        int success = 0;
        for (SearchSyncFailStore.SyncFailRecord record : records) {
            if (retry(record)) {
                success++;
            }
        }
        int dead = failStore.countByStatus("DEAD");
        if (dead > 0) {
            log.error("索引同步补偿存在 DEAD 记录（超过最大重试次数，需人工介入）: count={}", dead);
        }
        log.info("索引同步补偿完成: total={}, success={}", records.size(), success);
    }

    private boolean retry(SearchSyncFailStore.SyncFailRecord record) {
        DomainConfig domain = registry.byId(record.getDomainId());
        if (domain == null) {
            // 域已下线：记录出队，避免永久堆积
            failStore.markSuccess(record.getId());
            return true;
        }
        try {
            if (SearchSyncFailStore.OP_DELETE.equals(record.getOpType())
                    && !record.getDocId().startsWith("unknown:")) {
                syncService.syncDelete(domain, record.getDocId());
            } else {
                syncService.syncUpsert(domain, record.getDocId());
            }
            failStore.markSuccess(record.getId());
            return true;
        } catch (Exception ex) {
            log.warn("索引同步补偿重试失败: domain={}, docId={}, retryCount={}",
                    record.getDomainId(), record.getDocId(), record.getRetryCount(), ex);
            failStore.markFail(record.getId(), record.getRetryCount(),
                    properties.getRetryBackoffMinutes(), properties.getMaxRetryCount());
            return false;
        }
    }
}
