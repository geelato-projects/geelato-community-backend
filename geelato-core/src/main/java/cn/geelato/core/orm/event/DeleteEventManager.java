package cn.geelato.core.orm.event;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.ToIntFunction;

/**
 * 删除事件管理器。
 *
 * <p>触发/线程池/优先级契约与 {@link SaveEventManager} 完全对称，详见其 Javadoc。
 */
@Slf4j
public final class DeleteEventManager {
    private static final List<BeforeDeleteEventListener> BEFORE_LISTENERS = new CopyOnWriteArrayList<>();
    private static final List<AfterDeleteEventListener> AFTER_LISTENERS = new CopyOnWriteArrayList<>();
    private static final Object EXECUTOR_LOCK = new Object();

    private static volatile ExecutorService executor = EventExecutorFactory.defaultExecutor("delete-event-");

    private DeleteEventManager() {
    }

    // ===== 注册（B1：按 order 升序插入） =====

    public static void registerBefore(BeforeDeleteEventListener listener) {
        registerOrderedBefore(listener, false);
    }

    public static void registerBeforeIfAbsent(BeforeDeleteEventListener listener) {
        registerOrderedBefore(listener, true);
    }

    private static void registerOrderedBefore(BeforeDeleteEventListener listener, boolean ifAbsent) {
        if (listener == null) {
            return;
        }
        synchronized (BEFORE_LISTENERS) {
            if (ifAbsent && BEFORE_LISTENERS.contains(listener)) {
                return;
            }
            insertOrdered(BEFORE_LISTENERS, listener, BeforeDeleteEventListener::getOrder);
        }
    }

    public static void unregisterBefore(BeforeDeleteEventListener listener) {
        if (listener != null) {
            BEFORE_LISTENERS.remove(listener);
        }
    }

    public static void clearBefore() {
        BEFORE_LISTENERS.clear();
    }

    public static void registerAfter(AfterDeleteEventListener listener) {
        registerOrderedAfter(listener, false);
    }

    public static void registerAfterIfAbsent(AfterDeleteEventListener listener) {
        registerOrderedAfter(listener, true);
    }

    private static void registerOrderedAfter(AfterDeleteEventListener listener, boolean ifAbsent) {
        if (listener == null) {
            return;
        }
        synchronized (AFTER_LISTENERS) {
            if (ifAbsent && AFTER_LISTENERS.contains(listener)) {
                return;
            }
            insertOrdered(AFTER_LISTENERS, listener, AfterDeleteEventListener::getOrder);
        }
    }

    public static void unregisterAfter(AfterDeleteEventListener listener) {
        if (listener != null) {
            AFTER_LISTENERS.remove(listener);
        }
    }

    public static void clearAfter() {
        AFTER_LISTENERS.clear();
    }

    // ===== C3：函数式 callback 注册（默认 order=0） =====

    public static void registerBeforeCallback(cn.geelato.core.orm.event.callback.BeforeDeleteCallback callback) {
        registerBeforeCallback(callback, 0);
    }

    public static void registerBeforeCallback(cn.geelato.core.orm.event.callback.BeforeDeleteCallback callback, int order) {
        if (callback != null) {
            registerBeforeIfAbsent(cn.geelato.core.orm.event.callback.CallbackAdapters.forBeforeDelete(callback, order));
        }
    }

    public static void registerAfterCallback(cn.geelato.core.orm.event.callback.AfterDeleteCallback callback) {
        registerAfterCallback(callback, 0);
    }

    public static void registerAfterCallback(cn.geelato.core.orm.event.callback.AfterDeleteCallback callback, int order) {
        if (callback != null) {
            registerAfterIfAbsent(cn.geelato.core.orm.event.callback.CallbackAdapters.forAfterDelete(callback, order));
        }
    }

    private static <T> void insertOrdered(List<T> list, T item, ToIntFunction<T> orderFn) {
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

    // ===== 触发 =====

    public static void fireBefore(DeleteEventContext context) {
        if (log.isInfoEnabled()) {
            log.info("delete-event before start, eventId={}, commandEntity={}",
                    context.getEventId(),
                    context.getCommand() != null ? context.getCommand().getEntityName() : "null");
        }
        for (BeforeDeleteEventListener l : BEFORE_LISTENERS) {
            if (l.enabled(context) && l.supports(context)) {
                try {
                    if (log.isDebugEnabled()) {
                        log.debug("delete-event before dispatch listener={}, eventId={}",
                                l.getClass().getName(), context.getEventId());
                    }
                    l.beforeDelete(context);
                    if (log.isDebugEnabled()) {
                        log.debug("delete-event before done listener={}, eventId={}",
                                l.getClass().getName(), context.getEventId());
                    }
                } catch (Exception ex) {
                    log.error("delete-event before error listener={}, eventId={}", l.getClass().getName(), context.getEventId(), ex);
                    throw ex;
                }
            }
        }
        if (log.isInfoEnabled()) {
            log.info("delete-event before end, eventId={}", context.getEventId());
        }
    }

    public static void fireAfter(DeleteEventContext context) {
        if (log.isInfoEnabled()) {
            log.info("delete-event after schedule, eventId={}", context.getEventId());
        }
        for (AfterDeleteEventListener l : AFTER_LISTENERS) {
            if (l.enabled(context) && l.supports(context)) {
                // A2：事务感知监听器
                if (l instanceof TransactionalAfterDeleteEventListener) {
                    final TransactionalAfterDeleteEventListener tl = (TransactionalAfterDeleteEventListener) l;
                    context.onCommit(() -> safeRunAfterCommit(tl, context));
                    context.onRollback(() -> safeRunAfterRollback(tl, context));
                }
                final AfterDeleteEventListener listener = l;
                executor.submit(() -> {
                    try {
                        if (log.isDebugEnabled()) {
                            log.debug("delete-event after start listener={}, eventId={}", listener.getClass().getName(), context.getEventId());
                        }
                        listener.afterDelete(context);
                        if (log.isDebugEnabled()) {
                            log.debug("delete-event after end listener={}, eventId={}", listener.getClass().getName(), context.getEventId());
                        }
                    } catch (Exception ex) {
                        log.error("delete-event after error listener={}, eventId={}", listener.getClass().getName(), context.getEventId(), ex);
                    }
                });
            }
        }
    }

    private static void safeRunAfterCommit(TransactionalAfterDeleteEventListener tl, DeleteEventContext context) {
        try {
            tl.afterCommit(context);
        } catch (Exception ex) {
            log.error("delete-event afterCommit error listener={}, eventId={}", tl.getClass().getName(), context.getEventId(), ex);
        }
    }

    private static void safeRunAfterRollback(TransactionalAfterDeleteEventListener tl, DeleteEventContext context) {
        try {
            tl.afterRollback(context);
        } catch (Exception ex) {
            log.error("delete-event afterRollback error listener={}, eventId={}", tl.getClass().getName(), context.getEventId(), ex);
        }
    }

    // ===== 线程池管理（A4） =====

    public static void setExecutor(ExecutorService customExecutor) {
        if (customExecutor == null) {
            return;
        }
        synchronized (EXECUTOR_LOCK) {
            ExecutorService old = executor;
            executor = customExecutor;
            gracefulShutdown(old, "delete-event");
        }
    }

    public static void shutdown() {
        synchronized (EXECUTOR_LOCK) {
            gracefulShutdown(executor, "delete-event");
        }
    }

    private static void gracefulShutdown(ExecutorService pool, String name) {
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
