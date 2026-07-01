package cn.geelato.datasource.spi;

import java.util.List;
import java.util.Map;

/**
 * 动态数据源定义加载器。
 * 允许上层替换默认的 platform_dev_db_connect 表读取实现。
 */
public interface DynamicDataSourceDefinitionLoader {

    List<Map<String, Object>> loadAll();

    Map<String, Object> loadOne(String key);
}
