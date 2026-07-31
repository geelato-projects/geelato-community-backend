package cn.geelato.core.orm.event;

/**
 * 保存事件监听器基础接口。
 *
 * <p><b>阶段契约</b>：
 * <ul>
 *   <li>{@code beforeSave} 在业务 SQL 执行<b>前</b>同步触发；监听器抛出的异常会<b>透传</b>给调用方
 *       （可能中断业务写）。若监听器是 best-effort（如审计、埋点），<b>实现者应自行 try/catch 吞掉异常</b>，
 *       避免影响主流程；是否阻断业务由监听器实现者决定，框架不做全局开关。</li>
 *   <li>{@code afterSave} 在 SQL 执行<b>后</b>异步触发（事务提交前调度）；监听器异常仅记录日志，不影响业务。
 *       因异步且事务可见性不确定，需要事务感知的副作用请改用 {@link TransactionalAfterSaveEventListener}。</li>
 * </ul>
 *
 * <p><b>开关契约（B3 语义澄清）</b>：
 * <ul>
 *   <li>{@link #enabled} = 配置级粗开关，应廉价（只读 properties/常量），判断该监听器是否全局启用。</li>
 *   <li>{@link #supports} = 单次事件级细匹配，可查元数据/解析 SQL，判断该监听器是否处理本次特定事件。</li>
 *   <li>二者均须为 true 才触发回调；默认都返回 {@code false}（保守，须显式开启）。</li>
 * </ul>
 *
 * <p><b>优先级（B1）</b>：{@link #getOrder} 控制同阶段监听器执行顺序，值小先执行（默认 0）。
 */
public interface SaveEventListener {
    void beforeSave(SaveEventContext context);
    void afterSave(SaveEventContext context);

    /** 单次事件级匹配（细粒度，可查元数据）。默认 false。 */
    default boolean supports(SaveEventContext context) {
        return false;
    }

    /** 配置级开关（粗粒度，应廉价）。默认 false。 */
    default boolean enabled(SaveEventContext context) {
        return false;
    }

    /**
     * 监听器优先级，值小先执行（默认 0）。
     * 仅在同阶段（before/after）内生效；注册时按 order 升序插入。
     */
    default int getOrder() {
        return 0;
    }
}
