package cn.geelato.core.orm.event;

/**
 * 删除事件监听器基础接口。
 *
 * <p>阶段/开关/优先级契约与 {@link SaveEventListener} 对称：
 * <ul>
 *   <li>{@code beforeDelete} 同步触发，异常透传（best-effort 监听器应自行 try/catch 吞掉）。</li>
 *   <li>{@code afterDelete} 异步触发，异常仅记录日志；需事务感知请用 {@link TransactionalAfterDeleteEventListener}。</li>
 *   <li>{@link #enabled} 配置级粗开关、{@link #supports} 单次事件细匹配，默认均 false。</li>
 *   <li>{@link #getOrder} 优先级，值小先执行（默认 0）。</li>
 * </ul>
 */
public interface DeleteEventListener {
    void beforeDelete(DeleteEventContext context);
    void afterDelete(DeleteEventContext context);

    /** 单次事件级匹配（细粒度，可查元数据）。默认 false。 */
    default boolean supports(DeleteEventContext context) {
        return false;
    }

    /** 配置级开关（粗粒度，应廉价）。默认 false。 */
    default boolean enabled(DeleteEventContext context) {
        return false;
    }

    /** 监听器优先级，值小先执行（默认 0）。 */
    default int getOrder() {
        return 0;
    }
}
