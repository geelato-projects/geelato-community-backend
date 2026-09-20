package cn.geelato.search.lucene.sync;

import cn.geelato.core.orm.event.AfterSaveEventListener;
import cn.geelato.core.orm.event.SaveEventContext;
import cn.geelato.search.lucene.compensate.SearchSyncFailStore;
import lombok.extern.slf4j.Slf4j;

/**
 * 保存事件索引同步监听器（异步，由 ORM 事件线程池调度）。
 *
 * <p>挂在平台 ORM 既有 after 事件上：SQL 失败不触发、监听器异常仅记日志不传播，
 * CRUD 性能与可用性不受索引同步影响。失败一律落补偿队列
 * （引擎异常 ENGINE / 回表异常 READ_BACK / 行不可读竞态 MISSING_ROW / 主键缺失 MISSING_PK），
 * 由补偿任务退避重试收敛。
 *
 * <p>子实体（如柜号表）保存事件经外键定位主文档整体重建；
 * 外键值优先取本次提交值，缺失时回表读子行外键。
 */
@Slf4j
public class SearchSyncSaveListener implements AfterSaveEventListener {

    private final SearchDomainRegistry registry;
    private final SearchSyncService syncService;
    private final SearchSyncFailStore failStore;

    public SearchSyncSaveListener(SearchDomainRegistry registry, SearchSyncService syncService,
                                  SearchSyncFailStore failStore) {
        this.registry = registry;
        this.syncService = syncService;
        this.failStore = failStore;
    }

    @Override
    public boolean enabled(SaveEventContext context) {
        return registry.hasDomains();
    }

    @Override
    public boolean supports(SaveEventContext context) {
        if (context.getCommand() == null || context.getCommand().getEntityName() == null) {
            return false;
        }
        return registry.resolveSyncEntity(context.getCommand().getEntityName()) != null;
    }

    @Override
    public void beforeSave(SaveEventContext context) {
        // 不参与 before（同步阶段异常透传会影响业务写；索引同步只需 after 副本）
    }

    @Override
    public void afterSave(SaveEventContext context) {
        SearchDomainRegistry.SyncEntityMatch match = context.getCommand() == null ? null
                : registry.resolveSyncEntity(context.getCommand().getEntityName());
        String docId = null;
        try {
            if (match == null) {
                return;
            }
            docId = resolveDocId(context, match);
            if (docId == null) {
                return;
            }
            syncService.syncUpsert(match.domain(), docId);
        } catch (Exception ex) {
            log.error("搜索索引同步失败（已入补偿队列）: entity={}, docId={}, eventId={}",
                    context.getCommand() != null ? context.getCommand().getEntityName() : null,
                    docId, context.getEventId(), ex);
            // docId 未知时以事件 id 记录（MISSING_PK），补偿无法定位、由对账兜底
            failStore.record(match.domain().getId(),
                    docId != null ? docId : "unknown:" + context.getEventId(),
                    context.getCommand().getEntityName(), SearchSyncFailStore.OP_UPSERT,
                    classify(ex), ex);
        }
    }

    /** 主实体事件 docId=主键；子实体事件 docId=外键定位的主文档 id。 */
    private String resolveDocId(SaveEventContext context, SearchDomainRegistry.SyncEntityMatch match) {
        if (!context.isSuccess()) {
            return null;
        }
        String entityName = context.getCommand().getEntityName();
        String pk = context.getCommand().getPK();
        if (pk == null || pk.isBlank()) {
            throw new IllegalStateException("保存事件缺少主键: entity=" + entityName);
        }
        if (match.child() == null) {
            return pk;
        }
        Object fk = context.getCommand().getValueMap() == null
                ? null : context.getCommand().getValueMap().get(match.child().getFkField());
        String fkValue = fk != null ? String.valueOf(fk)
                : syncService.getDocumentLoader().loadChildFk(match.domain(), match.child(), pk);
        if (fkValue == null || fkValue.isBlank()) {
            throw new SearchSyncService.SyncMissException("子行外键不可得: entity=" + entityName + ", pk=" + pk);
        }
        return fkValue;
    }

    private String classify(Exception ex) {
        return ex instanceof SearchSyncService.SyncMissException
                ? SearchSyncFailStore.REASON_MISSING_ROW : SearchSyncFailStore.REASON_ENGINE;
    }
}
