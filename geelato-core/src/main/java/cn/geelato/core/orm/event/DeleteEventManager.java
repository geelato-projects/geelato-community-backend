package cn.geelato.core.orm.event;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public final class DeleteEventManager {
    private static final List<BeforeDeleteEventListener> BEFORE_LISTENERS = new CopyOnWriteArrayList<>();
    private static final List<AfterDeleteEventListener> AFTER_LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile ExecutorService executor = Executors.newFixedThreadPool(4, new ThreadFactory() {
        private final AtomicInteger idx = new AtomicInteger(1);
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "delete-event-" + idx.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    });

    private DeleteEventManager() {}

    public static void registerBefore(BeforeDeleteEventListener listener) {
        if (listener != null) {
            BEFORE_LISTENERS.add(listener);
        }
    }
    public static void registerBeforeIfAbsent(BeforeDeleteEventListener listener) {
        if (listener != null && !BEFORE_LISTENERS.contains(listener)) {
            BEFORE_LISTENERS.add(listener);
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
        if (listener != null) {
            AFTER_LISTENERS.add(listener);
        }
    }
    public static void registerAfterIfAbsent(AfterDeleteEventListener listener) {
        if (listener != null && !AFTER_LISTENERS.contains(listener)) {
            AFTER_LISTENERS.add(listener);
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
                executor.submit(() -> {
                    try {
                        if (log.isDebugEnabled()) {
                            log.debug("delete-event after start listener={}, eventId={}", l.getClass().getName(), context.getEventId());
                        }
                        l.afterDelete(context);
                        if (log.isDebugEnabled()) {
                            log.debug("delete-event after end listener={}, eventId={}", l.getClass().getName(), context.getEventId());
                        }
                    } catch (Exception ex) {
                        log.error("delete-event after error listener={}, eventId={}", l.getClass().getName(), context.getEventId(), ex);
                    }
                });
            }
        }
    }

    public static void setExecutor(ExecutorService customExecutor) {
        if (customExecutor != null) {
            executor = customExecutor;
        }
    }
}
