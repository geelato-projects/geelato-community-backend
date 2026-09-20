package cn.geelato.search.lucene.sync;

import cn.geelato.core.mql.command.DeleteCommand;
import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.core.orm.event.AfterDeleteEventListener;
import cn.geelato.core.orm.event.DeleteEventContext;
import cn.geelato.search.lucene.compensate.SearchSyncFailStore;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 物理删除事件索引同步监听器（异步）。
 *
 * <p>平台业务删除以软删为主（走保存事件更新 delStatus，本监听器不涉及）。
 * 物理删除时从删除条件提取主键/外键：主实体按主键删文档；子实体按外键重建主文档。
 * 条件中提取不到定位键时记补偿 MISSING_PK，由对账兜底——不静默放过。
 */
@Slf4j
public class SearchSyncDeleteListener implements AfterDeleteEventListener {

    private final SearchDomainRegistry registry;
    private final SearchSyncService syncService;
    private final SearchSyncFailStore failStore;

    public SearchSyncDeleteListener(SearchDomainRegistry registry, SearchSyncService syncService,
                                    SearchSyncFailStore failStore) {
        this.registry = registry;
        this.syncService = syncService;
        this.failStore = failStore;
    }

    @Override
    public boolean enabled(DeleteEventContext context) {
        return registry.hasDomains();
    }

    @Override
    public boolean supports(DeleteEventContext context) {
        if (context.getCommand() == null || context.getCommand().getEntityName() == null) {
            return false;
        }
        return registry.resolveSyncEntity(context.getCommand().getEntityName()) != null;
    }

    @Override
    public void beforeDelete(DeleteEventContext context) {
        // 不参与 before
    }

    @Override
    public void afterDelete(DeleteEventContext context) {
        DeleteCommand command = context.getCommand();
        SearchDomainRegistry.SyncEntityMatch match = command == null ? null
                : registry.resolveSyncEntity(command.getEntityName());
        if (match == null || !context.isSuccess() || context.getAffectedRows() <= 0) {
            return;
        }
        String entityName = command.getEntityName();
        try {
            FilterGroup where = command.getWhere();
            String locateField = match.child() == null
                    ? match.domain().getPkField() : match.child().getFkField();
            List<String> ids = extractEqValues(where, locateField);
            if (ids.isEmpty()) {
                throw new IllegalStateException(
                        "物理删除条件中无 " + locateField + " 定位值: entity=" + entityName);
            }
            if (match.child() == null) {
                syncService.syncDelete(match.domain(), ids.get(0));
            } else {
                syncService.syncUpsert(match.domain(), ids.get(0));
            }
        } catch (Exception ex) {
            log.error("删除事件索引同步失败（已入补偿队列）: entity={}, eventId={}",
                    entityName, context.getEventId(), ex);
            failStore.record(match.domain().getId(), "unknown:" + context.getEventId(), entityName,
                    SearchSyncFailStore.OP_DELETE, SearchSyncFailStore.REASON_MISSING_PK, ex);
        }
    }

    /** 从删除条件提取指定字段的 eq 值（仅支持确定性的等值条件）。 */
    private List<String> extractEqValues(FilterGroup where, String field) {
        List<String> values = new ArrayList<>();
        if (where == null) {
            return values;
        }
        collectEqValues(where, field, values);
        return values;
    }

    private void collectEqValues(FilterGroup group, String field, List<String> values) {
        if (group == null) {
            return;
        }
        for (FilterGroup.Filter filter : group.getFilters()) {
            if (filter.getFilterFieldType() == FilterGroup.FilterFieldType.Normal
                    && field.equals(filter.getField())
                    && filter.getOperator() == FilterGroup.Operator.eq
                    && filter.getValue() != null) {
                values.add(filter.getValue());
            }
        }
        for (FilterGroup child : group.getChildFilterGroup()) {
            collectEqValues(child, field, values);
        }
    }
}
