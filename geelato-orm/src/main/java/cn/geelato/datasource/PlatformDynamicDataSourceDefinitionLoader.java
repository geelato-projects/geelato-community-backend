package cn.geelato.datasource;

import cn.geelato.datasource.spi.DynamicDataSourceDefinitionLoader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

/**
 * 默认基于 platform_dev_db_connect 的动态数据源定义加载器。
 */
public class PlatformDynamicDataSourceDefinitionLoader implements DynamicDataSourceDefinitionLoader {
    private static final String SQL_ALL = "SELECT * FROM platform_dev_db_connect";
    private static final String SQL_ONE = "SELECT * FROM platform_dev_db_connect WHERE id = ?";

    private final JdbcTemplate primaryJdbcTemplate;

    public PlatformDynamicDataSourceDefinitionLoader(@Qualifier("primaryJdbcTemplate") JdbcTemplate primaryJdbcTemplate) {
        this.primaryJdbcTemplate = primaryJdbcTemplate;
    }

    @Override
    public List<Map<String, Object>> loadAll() {
        return primaryJdbcTemplate.queryForList(SQL_ALL);
    }

    @Override
    public Map<String, Object> loadOne(String key) {
        List<Map<String, Object>> dbConnectMaps = primaryJdbcTemplate.queryForList(SQL_ONE, key);
        return dbConnectMaps.isEmpty() ? null : dbConnectMaps.get(0);
    }
}
