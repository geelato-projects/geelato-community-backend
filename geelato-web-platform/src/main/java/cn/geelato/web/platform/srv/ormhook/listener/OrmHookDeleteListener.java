package cn.geelato.web.platform.srv.ormhook.listener;

import cn.geelato.core.orm.event.DeleteEventContext;
import cn.geelato.core.orm.event.TransactionalAfterDeleteEventListener;
import cn.geelato.web.platform.srv.ormhook.enums.OrmHookEventEnum;
import cn.geelato.web.platform.srv.ormhook.service.HookDispatchService;
import cn.geelato.web.platform.srv.ormhook.service.OrmHookRegistry;
import cn.geelato.web.platform.srv.ormhook.spi.OrmHookDepthHolder;
import lombok.extern.slf4j.Slf4j;

/**
 * ORM 钩子——删除事件监听器（与 {@link OrmHookSaveListener} 对称）。
 * <p>
 * 仅物理删除走本监听器（物理删除才产生 DeleteCommand/DeleteEventContext），命中 delete 规则；
 * <b>逻辑删除以 Update 形式走保存链路</b>（update 事件），payload 的 values 含 del_status=1。
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
        return registry.hasRules();
    }

    @Override
    public boolean supports(DeleteEventContext context) {
        if (OrmHookDepthHolder.isInHookExecution()) {
            return false;
        }
        if (context.getCommand() == null || context.getCommand().getEntityName() == null) {
            return false;
        }
        return registry.hasRuleFor(context.getCommand().getEntityName(), OrmHookEventEnum.DELETE);
    }

    @Override
    public void beforeDelete(DeleteEventContext context) {
        // v1 无 before 同步钩子
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
