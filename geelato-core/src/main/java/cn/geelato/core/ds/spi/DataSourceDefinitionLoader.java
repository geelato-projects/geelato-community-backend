package cn.geelato.core.ds.spi;

import cn.geelato.core.orm.Dao;

import java.util.List;
import java.util.Map;

/**
 * 动态数据源定义加载器。
 */
public interface DataSourceDefinitionLoader {

    List<Map<String, Object>> load(Dao dao);
}
