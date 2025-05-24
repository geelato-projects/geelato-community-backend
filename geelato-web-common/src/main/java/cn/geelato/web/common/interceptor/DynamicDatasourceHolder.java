package cn.geelato.web.common.interceptor;

public class DynamicDatasourceHolder {
    private static final ThreadLocal<String> dataSourceContextHolder = new ThreadLocal<>();
    public static String getDataSourceKey() {
        return dataSourceContextHolder.get();
    }
    public static void setDataSourceKey(String dataSourceKey) {
        dataSourceContextHolder.set(dataSourceKey);
    }
}
