package cn.geelato.datasource;

import cn.geelato.core.meta.MetaManager;

/**
 * 物化视图实体写门禁。
 * <p>table_type=materialized 的实体由外部物化引擎增量维护，平台侧只读；
 * 写方法名单复用 {@link DataSourceOverrideContext#isWriteMethod(String)}。
 */
public final class MaterializedViewGuard {

    private MaterializedViewGuard() {
    }

    /**
     * 写方法作用于物化视图实体时抛出明确异常，写操作应指向源库原表实体。
     *
     * @param methodName Dao 方法名
     * @param entityName 实体名，无法识别时跳过（与数据源解析的行为一致）
     */
    public static void assertWritable(String methodName, String entityName) {
        if (!DataSourceOverrideContext.isWriteMethod(methodName)) {
            return;
        }
        if (MetaManager.singleInstance().isMaterializedEntity(entityName)) {
            throw new IllegalStateException(String.format(
                    "物化视图实体 %s 为只读（由其物化引擎增量维护），禁止写操作：%s；写操作请指向源库原表实体",
                    entityName, methodName));
        }
    }
}
