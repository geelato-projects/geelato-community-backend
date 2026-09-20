package cn.geelato.search.api;

/**
 * 检索引擎能力声明：路由与同步接入层按能力适配降级。
 */
public class SearchEngineCapabilities {

    /** 写入一致性模式。 */
    public enum Consistency {
        /** 写入失败立即抛出（嵌入式引擎，如 Lucene）。 */
        SYNC_FAIL_FAST,
        /** 异步复制、最终一致（独立集群，如 ES）。 */
        ASYNC_EVENTUAL
    }

    private final Consistency consistency;
    /** 写入后是否无需显式 refresh 即可检索（近实时）。 */
    private final boolean nearRealtime;
    /** 单次检索可返回的最大 id 数量。 */
    private final int maxIdsLimit;

    public SearchEngineCapabilities(Consistency consistency, boolean nearRealtime, int maxIdsLimit) {
        this.consistency = consistency;
        this.nearRealtime = nearRealtime;
        this.maxIdsLimit = maxIdsLimit;
    }

    public Consistency getConsistency() {
        return consistency;
    }

    public boolean isNearRealtime() {
        return nearRealtime;
    }

    public int getMaxIdsLimit() {
        return maxIdsLimit;
    }
}
