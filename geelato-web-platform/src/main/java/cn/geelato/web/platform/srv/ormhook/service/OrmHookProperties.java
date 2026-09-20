package cn.geelato.web.platform.srv.ormhook.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * ORM 钩子机制配置。前缀 {@code geelato.platform.ormhook}。
 * <p>
 * 总开关 {@code geelato.platform.ormhook.enabled}（默认开）：无任何规则时监听器 supports()
 * 仅是一次空 map 查找，趋近零成本；关闭时监听器与调度器均不装配。
 *
 * @author geelato
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "geelato.platform.ormhook")
public class OrmHookProperties {

    /** 发件箱调度扫描间隔（毫秒），默认 3s */
    private long intervalMs = 3000L;

    /** 单次扫描处理的发件箱条数上限 */
    private int batchSize = 50;

    /** 最大重试次数，达上限进入死信 */
    private int maxRetryCount = 5;

    /** 已完成（success/dead）发件箱行的保留天数，超期物理删除；0 表示不清理 */
    private int retentionDays = 7;

    /** 清理已完成发件箱行的间隔（小时），默认 6 小时一次 */
    private int cleanupIntervalHours = 6;

    /** afterCommit 分发线程池大小 */
    private int dispatchPoolSize = 4;

    /** afterCommit 分发线程池队列容量（有界，满时 CallerRunsPolicy 背压） */
    private int dispatchQueueCapacity = 1000;
}
