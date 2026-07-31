package cn.geelato.orm.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ORM 事件机制配置。
 *
 * <p>控制 save/delete 事件异步线程池的大小与队列容量（A4）。默认值与原 {@code newFixedThreadPool(4)}
 * 行为接近，但有界队列 + CallerRunsPolicy 提供背压，避免 OOM。
 *
 * <p>前缀 {@code geelato.orm.event}。
 */
@Setter
@Getter
@ConfigurationProperties(prefix = "geelato.orm.event")
public class OrmEventProperties {

    /** save 事件线程池大小。 */
    private final Pool save = new Pool(4, 1000);

    /** delete 事件线程池大小。 */
    private final Pool delete = new Pool(4, 1000);

    @Setter
    @Getter
    public static class Pool {
        /** 核心与最大线程数。 */
        private int poolSize;
        /** 有界队列容量。 */
        private int queueCapacity;

        public Pool() {
        }

        public Pool(int poolSize, int queueCapacity) {
            this.poolSize = poolSize;
            this.queueCapacity = queueCapacity;
        }
    }
}
