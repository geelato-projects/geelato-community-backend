package cn.geelato.core.orm.event;

import lombok.extern.slf4j.Slf4j;
import cn.geelato.core.orm.event.listener.ReadonlyShadowTableListener;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * 保存事件管理器。
 *
 * <p><b>触发契约</b>：
 * <ul>
 *   <li>{@link #fireBefore} 同步遍历，监听器异常透传（best-effort 监听器应自行吞异常）。</li>
 *   <li>{@link #fireAfter} 异步遍历，异常仅记录；同时检测 {@link TransactionalAfterSaveEventListener}
 *       并把 commit/rollback 回调登记到 context（事务感知，A2）。</li>
 * </ul>
 *
 * <p><b>线程池（A4）</b>：默认由 {@link EventExecutorFactory#defaultExecutor} 构造（有界队列 1000 +
 * CallerRunsPolicy 背压），可通过 {@link #setExecutor} 替换（替换前 shutdown 旧池）。{@link #shutdown}
 * 供容器销毁时优雅关闭。
 *
 * <p><b>优先级（B1）</b>：注册按 {@code getOrder()} 升序插入，值小先执行。
 */
@Slf4j
public final class SaveEventManager {
    private static final List<BeforeSaveEventListener> BEFORE_LISTENERS = new CopyOnWriteArrayList<>();
    private static final List<AfterSaveEventListener> AFTER_LISTENERS = new CopyOnWriteArrayList<>();

    /** 线程池替换/关闭的同步锁，避免并发 setExecutor 与 shutdown 竞态。 */
    private static final Object EXECUTOR_LOCK = new Object();

    private static volatile ExecutorService executor = EventExecutorFactory.defaultExecutor("save-event-");

    private SaveEventManager() {
    }

    static {
        registerAfterIfAbsent(new ReadonlyShadowTableListener());
    }


    public static void registerBefore(BeforeSaveEventListener listener) {
        registerOrderedBefore(listener, false);
    }

    public static void registerBeforeIfAbsent(BeforeSaveEventListener listener) {
        registerOrderedBefore(listener, true);
    }

    private static void registerOrderedBefore(BeforeSaveEventListener listener, boolean ifAbsent) {
        if (listener == null) {
            return;
        }
        synchronized (BEFORE_LISTENERS) {
            if (ifAbsent && BEFORE_LISTENERS.contains(listener)) {
                return;
            }
            insertOrdered(BEFORE_LISTENERS, listener, BeforeSaveEventListener::getOrder);
        }
    }

    public static void unregisterBefore(BeforeSaveEventListener listener) {
        if (listener != null) {
            BEFORE_LISTENERS.remove(listener);
        }
    }

    public static void clearBefore() {
        BEFORE_LISTENERS.clear();
    }

    public static void registerAfter(AfterSaveEventListener listener) {
        registerOrderedAfter(listener, false);
    }

    public static void registerAfterIfAbsent(AfterSaveEventListener listener) {
        registerOrderedAfter(listener, true);
    }

    private static void registerOrderedAfter(AfterSaveEventListener listener, boolean ifAbsent) {
        if (listener == null) {
            return;
        }
        synchronized (AFTER_LISTENERS) {
            if (ifAbsent && AFTER_LISTENERS.contains(listener)) {
                return;
            }
            insertOrdered(AFTER_LISTENERS, listener, AfterSaveEventListener::getOrder);
        }
    }

    public static void unregisterAfter(AfterSaveEventListener listener) {
        if (listener != null) {
            AFTER_LISTENERS.remove(listener);
        }
    }

    public static void clearAfter() {
        AFTER_LISTENERS.clear();
    }

    // ===== C3：函数式 callback 注册（默认 order=0） =====

    public static void registerBeforeCallback(cn.geelato.core.orm.event.callback.BeforeSaveCallback callback) {
        registerBeforeCallback(callback, 0);
    }

    public static void registerBeforeCallback(cn.geelato.core.orm.event.callback.BeforeSaveCallback callback, int order) {
        if (callback != null) {
            registerBeforeIfAbsent(cn.geelato.core.orm.event.callback.CallbackAdapters.forBeforeSave(callback, order));
        }
    }

    public static void registerAfterCallback(cn.geelato.core.orm.event.callback.AfterSaveCallback callback) {
        registerAfterCallback(callback, 0);
    }

    public static void registerAfterCallback(cn.geelato.core.orm.event.callback.AfterSaveCallback callback, int order) {
        if (callback != null) {
            registerAfterIfAbsent(cn.geelato.core.orm.event.callback.CallbackAdapters.forAfterSave(callback, order));
        }
    }

    /** 按 order 升序插入列表（保持稳定排序）。传入的 CopyOnWriteArrayList 支持按下标 add。 */
    private static <T> void insertOrdered(java.util.List<T> list, T item, java.util.function.ToIntFunction<T> orderFn) {
        int order = orderFn.applyAsInt(item);
        int insertAt = list.size();
        for (int i = 0; i < list.size(); i++) {
            if (order < orderFn.applyAsInt(list.get(i))) {
                insertAt = i;
                break;
            }
        }
        list.add(insertAt, item);
    }


    public static void fireBefore(SaveEventContext context) {
        if (log.isInfoEnabled()) {
            log.info("save-event before start, eventId={}, entity={}, commandEntity={}",
                    context.getEventId(),
                    context.getEntity() != null ? context.getEntity().getClass().getSimpleName() : "null",
                    context.getCommand() != null ? context.getCommand().getEntityName() : "null");
        }
        for (BeforeSaveEventListener l : BEFORE_LISTENERS) {
            if (l.enabled(context) && l.supports(context)) {
                try {
                    if (log.isDebugEnabled()) {
                        log.debug("save-event before dispatch listener={}, eventId={}",
                                l.getClass().getName(), context.getEventId());
                    }
                    l.beforeSave(context);
                    if (log.isDebugEnabled()) {
                        log.debug("save-event before done listener={}, eventId={}",
                                l.getClass().getName(), context.getEventId());
                    }
                } catch (Exception ex) {
                    log.error("save-event before error listener={}, eventId={}", l.getClass().getName(), context.getEventId(), ex);
                    throw ex;
                }
            }
        }
        if (log.isInfoEnabled()) {
            log.info("save-event before end, eventId={}", context.getEventId());
        }
    }

    public static void fireAfter(SaveEventContext context) {
        if (log.isInfoEnabled()) {
            log.info("save-event after schedule, eventId={}", context.getEventId());
        }
        for (AfterSaveEventListener l : AFTER_LISTENERS) {
            if (l.enabled(context) && l.supports(context)) {
                // A2：事务感知监听器——把回调登记到 context，由 Dao 侧在事务提交/回滚点触发
                if (l instanceof TransactionalAfterSaveEventListener tl) {
                    context.onCommit(() -> safeRunAfterCommit(tl, context));
                    context.onRollback(() -> safeRunAfterRollback(tl, context));
                }
                // 老的异步 after 行为保留（兼容）
                final AfterSaveEventListener listener = l;
                executor.submit(() -> {
                    try {
                        if (log.isDebugEnabled()) {
                            log.debug("save-event after start listener={}, eventId={}", listener.getClass().getName(), context.getEventId());
                        }
                        listener.afterSave(context);
                        if (log.isDebugEnabled()) {
                            log.debug("save-event after end listener={}, eventId={}", listener.getClass().getName(), context.getEventId());
                        }
                    } catch (Exception ex) {
                        log.error("save-event after error listener={}, eventId={}", listener.getClass().getName(), context.getEventId(), ex);
                    }
                });
            }
        }
    }

    private static void safeRunAfterCommit(TransactionalAfterSaveEventListener tl, SaveEventContext context) {
        try {
            tl.afterCommit(context);
        } catch (Exception ex) {
            log.error("save-event afterCommit error listener={}, eventId={}", tl.getClass().getName(), context.getEventId(), ex);
        }
    }

    private static void safeRunAfterRollback(TransactionalAfterSaveEventListener tl, SaveEventContext context) {
        try {
            tl.afterRollback(context);
        } catch (Exception ex) {
            log.error("save-event afterRollback error listener={}, eventId={}", tl.getClass().getName(), context.getEventId(), ex);
        }
    }


    /**
     * 替换线程池。替换前会优雅关闭旧池，避免线程泄漏；加锁防并发竞态。
     */
    public static void setExecutor(ExecutorService customExecutor) {
        if (customExecutor == null) {
            return;
        }
        synchronized (EXECUTOR_LOCK) {
            ExecutorService old = executor;
            executor = customExecutor;
            gracefulShutdown(old);
        }
    }

    /** 供容器销毁时优雅关闭线程池。 */
    public static void shutdown() {
        synchronized (EXECUTOR_LOCK) {
            gracefulShutdown(executor);
        }
    }

    private static void gracefulShutdown(ExecutorService pool) {
        if (pool == null) {
            return;
        }
        pool.shutdown();
        try {
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
