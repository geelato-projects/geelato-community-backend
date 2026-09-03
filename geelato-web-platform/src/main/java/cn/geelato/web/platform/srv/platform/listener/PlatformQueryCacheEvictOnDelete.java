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
        // 缓存 key 形如 mql:{tenant}:{entity}:{md5}:{suffix},":entity:" 冒号定界精确匹配实体段
        metaCache.removeCacheByPattern("*:" + entityName + ":*");
    }
}
