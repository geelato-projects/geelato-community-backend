package cn.geelato.web.platform.srv.platform.listener;

import cn.geelato.core.GlobalContext;
import cn.geelato.core.orm.event.AfterDeleteEventListener;
import cn.geelato.core.orm.event.DeleteEventContext;
import cn.geelato.web.platform.cache.MetaCacheProvider;

public class PlatformQueryCacheEvictOnDelete implements AfterDeleteEventListener {
    private final MetaCacheProvider<Object> metaCache = new MetaCacheProvider<>();
    @Override
    public void beforeDelete(DeleteEventContext context) {}

    @Override
    public void afterDelete(DeleteEventContext context) {
        if (!GlobalContext.getMetaQueryCacheOption()) {
            return;
        }
        String entityName = context.getCommand() != null ? context.getCommand().getEntityName() : null;
        if (entityName == null || entityName.isEmpty()) {
            return;
        }
        metaCache.removeCacheByPattern("query:" + entityName + "*");
    }
}
