package cn.geelato.exception.engine.trigger;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 变更事件分发器：有界队列 + 单消费线程，满则丢弃留痕（事件只影响时效不影响正确性，扫描兜底）。
 */
@Slf4j
public class ChangeEventDispatcher {

    public record PendingEvent(String entityName, String operation, String bizId, String tenantCode, String eventId) {
    }

    private final EntityEventReactor reactor;
    private final ArrayBlockingQueue<PendingEvent> queue;
    private final ExecutorService worker;

    public ChangeEventDispatcher(EntityEventReactor reactor, int capacity) {
        this.reactor = reactor;
        this.queue = new ArrayBlockingQueue<>(Math.max(16, capacity));
        this.worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "exception-event-worker");
            t.setDaemon(true);
            return t;
        });
        this.worker.submit(this::drainLoop);
    }

    /** 非阻塞入队；队列满丢弃留痕（下一轮扫描兜底复评）。 */
    public boolean dispatch(String entityName, String operation, String bizId, String tenantCode, String eventId) {
        boolean accepted = queue.offer(new PendingEvent(entityName, operation, bizId, tenantCode, eventId));
        if (!accepted) {
            log.warn("事件队列已满，丢弃 eventId={} entity={}（扫描兜底）", eventId, entityName);
        }
        return accepted;
    }

    private void drainLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                PendingEvent event = queue.poll(5, TimeUnit.SECONDS);
                if (event != null) {
                    reactor.onEvent(event.entityName(), event.operation(), event.bizId(), event.tenantCode(), event.eventId());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn("事件消费异常: {}", e.getMessage());
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        worker.shutdownNow();
    }
}
