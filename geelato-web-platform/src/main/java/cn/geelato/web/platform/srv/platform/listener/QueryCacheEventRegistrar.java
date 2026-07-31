package cn.geelato.web.platform.srv.platform.listener;

import cn.geelato.core.orm.event.DeleteEventManager;
import cn.geelato.core.orm.event.SaveEventManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

@Component
public class QueryCacheEventRegistrar {
    private PlatformQueryCacheEvictOnSave saveListener;
    private PlatformQueryCacheEvictOnDelete deleteListener;

    @PostConstruct
    public void register() {
        this.saveListener = new PlatformQueryCacheEvictOnSave();
        this.deleteListener = new PlatformQueryCacheEvictOnDelete();
        SaveEventManager.registerAfterIfAbsent(saveListener);
        DeleteEventManager.registerAfterIfAbsent(deleteListener);
    }

    @PreDestroy
    public void unregister() {
        // B2：容器销毁时注销监听器，防热部署泄漏
        if (saveListener != null) {
            SaveEventManager.unregisterAfter(saveListener);
        }
        if (deleteListener != null) {
            DeleteEventManager.unregisterAfter(deleteListener);
        }
    }
}
