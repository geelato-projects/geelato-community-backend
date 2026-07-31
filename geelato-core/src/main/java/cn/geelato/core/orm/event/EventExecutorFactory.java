package cn.geelato.core.orm.event;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 事件线程池工厂。
 *
 * <p>用于替代原来的 {@code Executors.newFixedThreadPool(4)}（无界队列、无背压、有 OOM 风险）。
 * 改为：有界队列 + {@link ThreadPoolExecutor.CallerRunsPolicy}（背压，不丢任务，提交线程自行执行）+
 * 可配置线程数与队列容量 + 守护线程。
 *
 * <p>由 {@code geelato-orm} 的 {@code OrmEventAutoConfiguration} 据 properties 构造后，
 * 通过 {@code SaveEventManager.setExecutor} / {@code DeleteEventManager.setExecutor} 注入。
 * 未配置时，{@link #defaultExecutor(String)} 提供与原行为一致的兜底（4 线程 + 1000 队列）。
 */
public final class EventExecutorFactory {

    private EventExecutorFactory() {
    }

    /**
     * 构造事件线程池。
     *
     * @param threadNamePrefix 线程名前缀（如 "save-event-"）
     * @param poolSize         核心与最大线程数
     * @param queueCapacity    有界队列容量（&lt;=0 视为 1000）
     * @return 线程池（已配置 CallerRunsPolicy）
     */
    public static ExecutorService create(String threadNamePrefix, int poolSize, int queueCapacity) {
        int ps = Math.max(poolSize, 1);
        int cap = queueCapacity > 0 ? queueCapacity : 1000;
        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(cap);
        ThreadFactory factory = new EventThreadFactory(threadNamePrefix);
        // CallerRunsPolicy：队列满时由提交线程执行，形成背压，不丢任务、不 OOM
        return new ThreadPoolExecutor(ps, ps, 0L, TimeUnit.MILLISECONDS, queue, factory,
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /** 与原 newFixedThreadPool(4) 行为最接近的兜底（但有界队列 1000 + CallerRunsPolicy）。 */
    public static ExecutorService defaultExecutor(String threadNamePrefix) {
        return create(threadNamePrefix, 4, 1000);
    }

    /** 守护线程工厂。 */
    private static final class EventThreadFactory implements ThreadFactory {
        private final AtomicInteger idx = new AtomicInteger(1);
        private final String prefix;

        EventThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + idx.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}
