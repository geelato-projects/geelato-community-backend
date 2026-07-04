package cn.geelato.datasource;

/**
 * 动态数据源上下文持有者
 * 使用ThreadLocal存储当前线程的数据源标识
 */
public class DynamicDataSourceHolder {
    private static final ThreadLocal<String> dataSourceContextHolder = new ThreadLocal<>();
    public static String getDataSourceKey() {
        return dataSourceContextHolder.get();
    }

    public static void setDataSourceKey(String dataSourceKey) {
        dataSourceContextHolder.set(dataSourceKey);
    }

    public static void clearDataSourceKey() {
        dataSourceContextHolder.remove();
    }
}
