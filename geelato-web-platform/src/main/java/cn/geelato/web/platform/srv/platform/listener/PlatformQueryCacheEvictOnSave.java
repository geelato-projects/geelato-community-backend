package cn.geelato.web.platform.srv.platform.listener;

import cn.geelato.core.GlobalContext;
import cn.geelato.core.orm.event.AfterSaveEventListener;
import cn.geelato.core.orm.event.SaveEventContext;
import cn.geelato.web.platform.cache.MetaCacheProvider;

public class PlatformQueryCacheEvictOnSave implements AfterSaveEventListener {
    private final MetaCacheProvider<Object> metaCache = new MetaCacheProvider<>();
    @Override
    public void beforeSave(SaveEventContext context) {}

    @Override
    public void afterSave(SaveEventContext context) {
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
