package cn.geelato.exception.boot;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "geelato.exception.engine")
public class ExceptionEngineProperties {

    /** 引擎总开关（缺省关闭，关闭时模块行为与占位现状完全一致）。 */
    private boolean enabled = false;

    private final Scan scan = new Scan();
    private final Pipeline pipeline = new Pipeline();
    private final Sql sql = new Sql();
    private final Http http = new Http();
    private final Trigger trigger = new Trigger();

    @Getter
    @Setter
    public static class Trigger {
        /** 事件队列容量（满丢弃留痕，扫描兜底）。 */
        private int queueCapacity = 1000;
    }

    @Getter
    @Setter
    public static class Scan {
        /** 调度 tick 间隔秒。 */
        private int tickSeconds = 30;
        /** 扫描执行线程池大小。 */
        private int threadPoolSize = 2;
        /** 扫描锁 stale 回收阈值分钟（CAS 条件内嵌）。 */
        private int lockStaleMinutes = 30;
    }

    @Getter
    @Setter
    public static class Pipeline {
        /** 单步超时毫秒。 */
        private long stepTimeoutMs = 30_000;
        /** 整管道超时毫秒。 */
        private long pipelineTimeoutMs = 120_000;
        /** 试运行/启用门禁受限超时毫秒。 */
        private long trialTimeoutMs = 30_000;
        /** 单步出口大小熔断（JSON 序列化字节数，超限该步失败）。 */
        private long stepOutputMaxBytes = 2 * 1024 * 1024;
    }

    @Getter
    @Setter
    public static class Sql {
        /** 业务库数据源定位（Bean 名，经容器解析；platform_exception 在主库、业务表在 ob 库，按宿主实际配置）。 */
        private String connectId = "primary";
    }

    @Getter
    @Setter
    public static class Http {
        private boolean enabled = false;
        private java.util.List<String> allowlist = new java.util.ArrayList<>();
        private int timeoutMs = 5_000;
    }
}
