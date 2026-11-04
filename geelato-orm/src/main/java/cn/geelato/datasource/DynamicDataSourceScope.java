package cn.geelato.datasource;

import org.springframework.util.StringUtils;

import java.util.function.Supplier;

/**
 * 数据源切换的唯一出入口工具。
 * <p>所有对 {@link DynamicDataSourceHolder} 的写入统一收敛至此（含经
 * {@code DataSourceInterceptor} 切面的自动设置）；业务代码按语义二选一：</p>
 * <ul>
 *   <li>{@link #withDataSource(String, Supplier)}——代码块作用域：块内生效，结束恢复原值；
 *       MQL 执行器、切面、异步任务等框架路径均走此语义；</li>
 *   <li>{@link #switchTo(String)}——请求内当前默认库：设置后持续生效直至下次切换或
 *       请求结束（由 web 层清理安全网
 *       {@code cn.geelato.web.common.filter.DataSourceContextCleanupFilter} 兜底回收）。</li>
 * </ul>
 *
 * <h3>路由解析优先级（两套消费链，实体档同源经 {@link EntityDataSourceResolver}）</h3>
 * <ol>
 *   <li><b>Dao 调用切面链</b>（{@code DataSourceInterceptor}，覆盖全部 Dao 方法）：
 *       实体显式归属（TableMeta.connectId &gt; catalog 映射）&gt; 类/方法级
 *       {@code @UseDynamicDataSource} 注解值 &gt; 外层已显式设置的 key（本类两方法产生）&gt;
 *       请求级覆盖（仅无显式归属实体，{@link DataSourceOverrideContext}）&gt;
 *       平台默认 key &gt; "primary"；</li>
 *   <li><b>MQL 执行器链</b>（{@code AbstractExecutionStrategySupport}）：
 *       DSL 显式 useDataSource &gt; 实体显式归属 &gt; 请求级覆盖 &gt; 平台默认 key &gt; "primary"。</li>
 * </ol>
 * <p>任何显式指定（实体归属/注解/DSL/代码块作用域）都不被请求级覆盖劫持。
 * 实体显式归属的数据源未注册时由 {@link EntityDataSourceResolver} 硬失败，不静默回退主库。</p>
 */
public final class DynamicDataSourceScope {

    private DynamicDataSourceScope() {
    }

    /**
     * 代码块作用域切换：connectId 有值时块内生效，无论正常/异常结束都恢复原值。
     */
    public static <T> T withDataSource(String connectId, Supplier<T> action) {
        String previous = DynamicDataSourceHolder.getDataSourceKey();
        try {
            if (StringUtils.hasText(connectId)) {
                DynamicDataSourceHolder.setDataSourceKey(connectId);
            }
            return action.get();
        } finally {
            restore(previous);
        }
    }

    /**
     * 请求内当前默认库切换：设置后持续生效（外层 key 语义），由请求级清理安全网兜底回收。
     */
    public static void switchTo(String connectId) {
        if (!StringUtils.hasText(connectId)) {
            throw new IllegalArgumentException("数据连接不能为空");
        }
        DynamicDataSourceHolder.setDataSourceKey(connectId);
    }

    private static void restore(String previous) {
        if (StringUtils.hasText(previous)) {
            DynamicDataSourceHolder.setDataSourceKey(previous);
        } else {
            DynamicDataSourceHolder.clearDataSourceKey();
        }
    }
}
