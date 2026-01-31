package cn.geelato.web.platform.srv.platform.listener;

import cn.geelato.core.orm.event.DeleteEventManager;
import cn.geelato.core.orm.event.SaveEventManager;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class QueryCacheEventRegistrar {
    @PostConstruct
    public void register() {
        SaveEventManager.registerAfterIfAbsent(new PlatformQueryCacheEvictOnSave());
        DeleteEventManager.registerAfterIfAbsent(new PlatformQueryCacheEvictOnDelete());
    }
}
