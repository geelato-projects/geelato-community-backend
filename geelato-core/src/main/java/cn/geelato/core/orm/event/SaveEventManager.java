package cn.geelato.core.orm.event;

import lombok.extern.slf4j.Slf4j;
import cn.geelato.core.orm.event.listener.ReadonlyShadowTableListener;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public final class SaveEventManager {
    private static final List<BeforeSaveEventListener> BEFORE_LISTENERS = new CopyOnWriteArrayList<>();
    private static final List<AfterSaveEventListener> AFTER_LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile ExecutorService executor = Executors.newFixedThreadPool(4, new ThreadFactory() {
        private final AtomicInteger idx = new AtomicInteger(1);
        @Override
        public Thread newThread(@NotNull Runnable r) {
            Thread t = new Thread(r, "save-event-" + idx.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    });

    private SaveEventManager() {}
    static {
        registerAfterIfAbsent(new ReadonlyShadowTableListener());
    }

    public static void registerBefore(BeforeSaveEventListener listener) {
        if (listener != null) {
            BEFORE_LISTENERS.add(listener);
        }
    }
    public static void registerBeforeIfAbsent(BeforeSaveEventListener listener) {
        if (listener != null && !BEFORE_LISTENERS.contains(listener)) {
            BEFORE_LISTENERS.add(listener);
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
        if (listener != null) {
            AFTER_LISTENERS.add(listener);
        }
    }
    public static void registerAfterIfAbsent(AfterSaveEventListener listener) {
        if (listener != null && !AFTER_LISTENERS.contains(listener)) {
            AFTER_LISTENERS.add(listener);
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
                executor.submit(() -> {
                    try {
                        if (log.isDebugEnabled()) {
                            log.debug("save-event after start listener={}, eventId={}", l.getClass().getName(), context.getEventId());
                        }
                        l.afterSave(context);
                        if (log.isDebugEnabled()) {
                            log.debug("save-event after end listener={}, eventId={}", l.getClass().getName(), context.getEventId());
                        }
                    } catch (Exception ex) {
                        log.error("save-event after error listener={}, eventId={}", l.getClass().getName(), context.getEventId(), ex);
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
