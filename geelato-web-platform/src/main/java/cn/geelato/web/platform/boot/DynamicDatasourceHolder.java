package cn.geelato.web.platform.boot;

public class DynamicDatasourceHolder {
    public static final ThreadLocal<String> dataSourceContextHolder = new ThreadLocal<String>();
    public static String getDataSourceKey() {
        return dataSourceContextHolder.get();
    }
    public static void setDataSourceKey(String dataSourceKey) {
        dataSourceContextHolder.set(dataSourceKey);
    }
}
