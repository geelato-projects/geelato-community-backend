package cn.geelato.exception.engine.core;

/**
 * 管道触发三态：扫描与复评跑同一条管道，口径不漂移。
 */
public enum TriggerMode {

    /** 定时扫描全量窗口。 */
    SCAN,
    /** 变更触发单对象复评（注入 $ctx.bizId）。 */
    RECHECK,
    /** 试运行 dry-run（受限超时，不落库不触达）。 */
    TRIAL
}
