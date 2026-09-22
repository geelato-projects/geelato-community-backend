package cn.geelato.web.platform.srv.ormhook.listener;

import cn.geelato.core.orm.event.DeleteEventContext;
import cn.geelato.core.orm.event.TransactionalAfterDeleteEventListener;
import cn.geelato.web.platform.srv.ormhook.service.HookDispatchService;
import cn.geelato.web.platform.srv.ormhook.service.OrmHookRegistry;
import cn.geelato.web.platform.srv.ormhook.spi.OrmHookDepthHolder;
import lombok.extern.slf4j.Slf4j;

/**
 * ORM 钩子——删除事件监听器（与 {@link OrmHookSaveListener} 对称）。
 * <p>
 * 仅物理删除走本监听器（物理删除才产生 DeleteCommand/DeleteEventContext），
 * 载荷 eventType=delete；<b>逻辑删除以 Update 形式走保存链路</b>（eventType=update，
 * values 含 del_status=1）。无实体/事件维度匹配——任何实体的删除事件都触发。
 * 契约同保存监听器：提交后异步、纯附加、失败不上抛、深度守卫防递归。
 * 非Spring Bean，由 {@code OrmHookRegistrar} 显式注册/注销。
 *
 * @author geelato
 */
@Slf4j
public class OrmHookDeleteListener implements TransactionalAfterDeleteEventListener {

    private final OrmHookRegistry registry;
    private final HookDispatchService dispatchService;

    public OrmHookDeleteListener(OrmHookRegistry registry, HookDispatchService dispatchService) {
        this.registry = registry;
        this.dispatchService = dispatchService;
    }

    @Override
    public int getOrder() {
        return 100;
    }

    @Override
    public boolean enabled(DeleteEventContext context) {
        return registry.hasHooks();
    }

    @Override
    public boolean supports(DeleteEventContext context) {
        if (OrmHookDepthHolder.isInHookExecution()) {
            return false;
        }
        if (context.getCommand() == null || context.getCommand().getEntityName() == null) {
            return false;
        }
        return !OrmHookRegistry.FORBIDDEN_TARGET_ENTITIES.contains(context.getCommand().getEntityName());
    }

    @Override
    public void beforeDelete(DeleteEventContext context) {
        // 无同步钩子
    }

    @Override
    public void afterDelete(DeleteEventContext context) {
        // 老异步契约不承载逻辑（事务边界不可靠）；逻辑统一在 afterCommit
    }

    @Override
    public void afterCommit(DeleteEventContext context) {
        try {
            dispatchService.dispatchDeleteCommit(context);
        } catch (Exception e) {
            log.error("ORM Hook afterCommit 分发异常 eventId={}: {}", context.getEventId(), e.getMessage(), e);
        }
    }

    @Override
    public void afterRollback(DeleteEventContext context) {
        // 回滚不触发钩子
    }
}
