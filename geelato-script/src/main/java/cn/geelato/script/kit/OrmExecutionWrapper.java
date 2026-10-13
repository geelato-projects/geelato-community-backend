package cn.geelato.script.kit;

import cn.geelato.script.core.IdentitySnapshot;

import java.util.function.Supplier;

/**
 * ORM 调用包裹：数据源路由（connectId）与线程上下文（SecurityContext/DynamicDataSourceHolder）
 * 重建 + finally 清理由宿主实现；SDK 缺省直跑（单数据源无 security 场景）。
 */
@FunctionalInterface
public interface OrmExecutionWrapper {

    <T> T run(String connectId, IdentitySnapshot identity, Supplier<T> action);

    OrmExecutionWrapper DIRECT = new OrmExecutionWrapper() {
        @Override
        public <T> T run(String connectId, IdentitySnapshot identity, Supplier<T> action) {
            return action.get();
        }
    };
}
