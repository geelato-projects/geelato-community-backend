package cn.geelato.datasource;

import cn.geelato.datasource.spi.DynamicDataSourceDefinitionLoader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 默认基于 platform_dev_db_connect 的动态数据源定义加载器。
 *
 * <p>本类位于业务层（geelato-web-platform），框架层（geelato-orm）仅保留
 * {@link DynamicDataSourceDefinitionLoader} SPI 接口。保留原 package（cn.geelato.datasource）
 * 以维持 split-package 的一致性，由 {@code @ComponentScan(basePackages = {"cn.geelato"})} 发现。</p>
 */
@Component
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
