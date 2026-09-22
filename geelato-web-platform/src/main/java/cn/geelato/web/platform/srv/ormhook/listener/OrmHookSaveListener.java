package cn.geelato.web.platform.srv.ormhook.listener;

import cn.geelato.core.orm.event.SaveEventContext;
import cn.geelato.core.orm.event.TransactionalAfterSaveEventListener;
import cn.geelato.web.platform.srv.ormhook.service.HookDispatchService;
import cn.geelato.web.platform.srv.ormhook.service.OrmHookRegistry;
import cn.geelato.web.platform.srv.ormhook.spi.OrmHookDepthHolder;
import lombok.extern.slf4j.Slf4j;

/**
 * ORM 钩子——保存事件监听器（{@link TransactionalAfterSaveEventListener} 的首个业务侧实现）。
 * <p>
 * 只在<b>事务真正提交后</b>触发（{@link #afterCommit}），纯附加特性：
 * <ul>
 *   <li>{@code enabled}（粗开关）：存在启用中的 Hook，一次内存判断</li>
 *   <li>{@code supports}（细匹配）：钩子执行深度=0（防递归）+ 实体非钩子机制自身两张表
 *       （硬排除防自触发）——<b>无实体/事件维度匹配</b>，任何实体的任何保存事件都触发，
 *       事件类型（insert/update）由分发时按 opType 标注进载荷</li>
 *   <li>{@code afterCommit}：快照 payload + 提交异步任务（见 {@link HookDispatchService}），
 *       全程 try-catch 吞异常——钩子任何失败不影响业务链路</li>
 *   <li>{@code beforeSave}/{@code afterSave}：留空（无同步钩子；afterSave 为老异步契约，
 *       事务边界不可靠，不承载逻辑）</li>
 *   <li>{@code afterRollback}：留空（回滚不触发钩子）</li>
 * </ul>
 * order=100：排在审计（旧值回查）等既有监听器之后，互不干扰。
 * 非Spring Bean，由 {@code OrmHookRegistrar} 显式注册/注销。
 *
 * @author geelato
 */
@Slf4j
public class OrmHookSaveListener implements TransactionalAfterSaveEventListener {

    private final OrmHookRegistry registry;
    private final HookDispatchService dispatchService;

    public OrmHookSaveListener(OrmHookRegistry registry, HookDispatchService dispatchService) {
        this.registry = registry;
        this.dispatchService = dispatchService;
    }

    @Override
    public int getOrder() {
        return 100;
    }

    @Override
    public boolean enabled(SaveEventContext context) {
        return registry.hasHooks();
    }

    @Override
    public boolean supports(SaveEventContext context) {
        if (OrmHookDepthHolder.isInHookExecution()) {
            return false;
        }
        if (context.getCommand() == null || context.getCommand().getEntityName() == null) {
            return false;
        }
        return !OrmHookRegistry.FORBIDDEN_TARGET_ENTITIES.contains(context.getCommand().getEntityName());
    }

    @Override
    public void beforeSave(SaveEventContext context) {
        // 无同步钩子
    }

    @Override
    public void afterSave(SaveEventContext context) {
        // 老异步契约不承载逻辑（事务边界不可靠）；逻辑统一在 afterCommit
    }

    @Override
    public void afterCommit(SaveEventContext context) {
        try {
            dispatchService.dispatchSaveCommit(context);
        } catch (Exception e) {
            // 双保险：EventTransactionSupport 本会吞回调异常，这里再兜底并留痕
            log.error("ORM Hook afterCommit 分发异常 eventId={}: {}", context.getEventId(), e.getMessage(), e);
        }
    }

    @Override
    public void afterRollback(SaveEventContext context) {
        // 回滚不触发钩子
    }
}
