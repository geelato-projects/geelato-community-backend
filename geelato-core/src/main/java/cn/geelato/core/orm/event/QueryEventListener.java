package cn.geelato.core.orm.event;

/**
 * 查询事件监听器基础接口（C2）。
 *
 * <p>用于查询拦截：读审计、慢查询统计、缓存预热、数据权限过滤等。
 *
 * <p>开关/优先级契约同 {@link SaveEventListener}：
 * <ul>
 *   <li>{@code enabled()} 配置级粗开关（廉价），{@code supports()} 单次事件细匹配，默认均 false。</li>
 *   <li>{@code getOrder()} 优先级，值小先执行（默认 0）。</li>
 * </ul>
 *
 * <p><b>beforeQuery</b> 同步触发：可抛异常阻断查询（如权限校验失败），best-effort 监听器应自行吞异常。
 * <p><b>afterQuery</b> 异步触发：异常仅记录，不影响业务。
 */
public interface QueryEventListener {
    void beforeQuery(QueryEventContext context);

    void afterQuery(QueryEventContext context);

    /** 单次事件级匹配（细粒度）。默认 false。 */
    default boolean supports(QueryEventContext context) {
        return false;
    }

    /** 配置级开关（粗粒度，应廉价）。默认 false。 */
    default boolean enabled(QueryEventContext context) {
        return false;
    }

    /** 监听器优先级，值小先执行（默认 0）。 */
    default int getOrder() {
        return 0;
    }
}
