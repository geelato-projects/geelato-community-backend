package cn.geelato.web.platform.srv.ormhook.listener;

import cn.geelato.core.orm.event.DeleteEventManager;
import cn.geelato.core.orm.event.SaveEventManager;
import cn.geelato.web.platform.srv.ormhook.service.HookDispatchService;
import cn.geelato.web.platform.srv.ormhook.service.OrmHookRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * ORM 钩子监听器注册器（对齐 {@code QueryCacheEventRegistrar} 模式）。
 * <p>
 * 总开关 {@code geelato.platform.ormhook.enabled}（默认开）：关闭时不注册监听器，钩子完全静止；
 * 无规则时监听器 enabled() 恒 false，趋近零成本——默认开箱即用，无需显式配置。
 *
 * @author geelato
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "geelato.platform.ormhook", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OrmHookRegistrar {

    private final OrmHookRegistry registry;
    private final HookDispatchService dispatchService;

    private OrmHookSaveListener saveListener;
    private OrmHookDeleteListener deleteListener;

    public OrmHookRegistrar(OrmHookRegistry registry, HookDispatchService dispatchService) {
        this.registry = registry;
        this.dispatchService = dispatchService;
    }

    @PostConstruct
    public void register() {
        this.saveListener = new OrmHookSaveListener(registry, dispatchService);
        this.deleteListener = new OrmHookDeleteListener(registry, dispatchService);
        SaveEventManager.registerAfterIfAbsent(saveListener);
        DeleteEventManager.registerAfterIfAbsent(deleteListener);
        log.info("ORM 钩子监听器已注册（after-commit 异步钩子，总开关默认开启）");
    }

    @PreDestroy
    public void unregister() {
        // 容器销毁时注销监听器，防热部署泄漏
        if (saveListener != null) {
            SaveEventManager.unregisterAfter(saveListener);
        }
        if (deleteListener != null) {
            DeleteEventManager.unregisterAfter(deleteListener);
        }
    }
}
