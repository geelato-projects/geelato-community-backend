package cn.geelato.web.platform.logging.es;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

public final class EsLogBuffer {
    private static volatile BlockingQueue<EsLogEvent> queue = new ArrayBlockingQueue<>(10000);
    private static final AtomicLong droppedCount = new AtomicLong(0);

    private EsLogBuffer() {
    }

    public static void resize(int capacity) {
        if (capacity <= 0) {
            return;
        }
        queue = new ArrayBlockingQueue<>(capacity);
    }

    public static void offer(EsLogEvent event) {
        if (event == null) {
            return;
        }
        if (!queue.offer(event)) {
            droppedCount.incrementAndGet();
        }
    }

    public static List<EsLogEvent> pollBatch(int size) {
        int limit = Math.max(size, 1);
        List<EsLogEvent> result = new ArrayList<>(limit);
        queue.drainTo(result, limit);
        return result;
    }

    public static long drainDroppedCount() {
        return droppedCount.getAndSet(0);
    }

    public static int size() {
        return queue.size();
    }
}
