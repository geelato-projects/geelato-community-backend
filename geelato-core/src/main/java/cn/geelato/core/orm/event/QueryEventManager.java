package cn.geelato.core.orm.event;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.ToIntFunction;

/**
 * 查询事件管理器（C2）。
 *
 * <p>触发/线程池/优先级契约与 {@link SaveEventManager} 完全对称：
 * <ul>
 *   <li>{@code fireBefore} 同步遍历，异常透传（best-effort 监听器应自行吞异常）。</li>
 *   <li>{@code fireAfter} 异步遍历，异常仅记录。</li>
 *   <li>注册按 {@code getOrder()} 升序插入（B1）。</li>
 * </ul>
 *
 * <p>查询事件无事务感知回调（查询通常无提交/回滚语义）。
 */
@Slf4j
public final class QueryEventManager {
    private static final List<BeforeQueryEventListener> BEFORE_LISTENERS = new CopyOnWriteArrayList<>();
    private static final List<AfterQueryEventListener> AFTER_LISTENERS = new CopyOnWriteArrayList<>();
    private static final Object EXECUTOR_LOCK = new Object();

    private static volatile ExecutorService executor = EventExecutorFactory.defaultExecutor("query-event-");

    private QueryEventManager() {
    }

    // ===== 注册（B1：按 order 升序插入） =====

    public static void registerBefore(BeforeQueryEventListener listener) {
        registerOrderedBefore(listener, false);
    }

    public static void registerBeforeIfAbsent(BeforeQueryEventListener listener) {
        registerOrderedBefore(listener, true);
    }

    private static void registerOrderedBefore(BeforeQueryEventListener listener, boolean ifAbsent) {
        if (listener == null) {
            return;
        }
        synchronized (BEFORE_LISTENERS) {
            if (ifAbsent && BEFORE_LISTENERS.contains(listener)) {
                return;
            }
            insertOrdered(BEFORE_LISTENERS, listener, BeforeQueryEventListener::getOrder);
        }
    }

    public static void unregisterBefore(BeforeQueryEventListener listener) {
        if (listener != null) {
            BEFORE_LISTENERS.remove(listener);
        }
    }

    public static void clearBefore() {
        BEFORE_LISTENERS.clear();
    }

    public static void registerAfter(AfterQueryEventListener listener) {
        registerOrderedAfter(listener, false);
    }

    public static void registerAfterIfAbsent(AfterQueryEventListener listener) {
        registerOrderedAfter(listener, true);
    }

    private static void registerOrderedAfter(AfterQueryEventListener listener, boolean ifAbsent) {
        if (listener == null) {
            return;
        }
        synchronized (AFTER_LISTENERS) {
            if (ifAbsent && AFTER_LISTENERS.contains(listener)) {
                return;
            }
            insertOrdered(AFTER_LISTENERS, listener, AfterQueryEventListener::getOrder);
        }
    }

    public static void unregisterAfter(AfterQueryEventListener listener) {
        if (listener != null) {
            AFTER_LISTENERS.remove(listener);
        }
    }

    public static void clearAfter() {
        AFTER_LISTENERS.clear();
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

    public static void fireBefore(QueryEventContext context) {
        if (BEFORE_LISTENERS.isEmpty()) {
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug("query-event before start, eventId={}", context.getEventId());
        }
        for (BeforeQueryEventListener l : BEFORE_LISTENERS) {
            if (l.enabled(context) && l.supports(context)) {
                try {
                    l.beforeQuery(context);
                } catch (Exception ex) {
                    log.error("query-event before error listener={}, eventId={}", l.getClass().getName(), context.getEventId(), ex);
                    throw ex;
                }
            }
        }
    }

    public static void fireAfter(QueryEventContext context) {
        if (AFTER_LISTENERS.isEmpty()) {
            return;
        }
        for (AfterQueryEventListener l : AFTER_LISTENERS) {
            if (l.enabled(context) && l.supports(context)) {
                final AfterQueryEventListener listener = l;
                executor.submit(() -> {
                    try {
                        listener.afterQuery(context);
                    } catch (Exception ex) {
                        log.error("query-event after error listener={}, eventId={}", listener.getClass().getName(), context.getEventId(), ex);
                    }
                });
            }
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
            gracefulShutdown(old);
        }
    }

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
