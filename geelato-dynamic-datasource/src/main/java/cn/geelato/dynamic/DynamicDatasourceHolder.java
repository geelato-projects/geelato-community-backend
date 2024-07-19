package cn.geelato.dynamic;

public class DynamicDatasourceHolder {
    public static final ThreadLocal<String> dataSourceContextHolder = new ThreadLocal<String>();
    public static String getDataSourceKey() {
        return dataSourceContextHolder.get();
    }
    public static void setDataSourceKey(String dataSourceKey) {
        dataSourceContextHolder.set(dataSourceKey);
    }
    public static void removeDataSourceKey(String dataSourceKey) {
        dataSourceContextHolder.remove();
    }
}
