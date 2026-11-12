package cn.geelato.core.ds.support;

import cn.geelato.core.ds.spi.DataSourceDefinitionLoader;
import cn.geelato.core.orm.Dao;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 当前平台表结构下的默认数据源定义加载器。
 *
 * <p>本类位于业务层（geelato-web-platform），框架层（geelato-core）仅保留
 * {@link DataSourceDefinitionLoader} SPI 接口。保留原 package（cn.geelato.core.ds.support）
 * 以维持 import 一致性，由 {@code @ComponentScan(basePackages = {"cn.geelato"})} 发现。</p>
 */
@Component
public class DefaultDataSourceDefinitionLoader implements DataSourceDefinitionLoader {

    private static final String SQL_CONNECT_LIST = "SELECT * FROM platform_dev_db_connect";
    private static final String SQL_CONNECT_ONE = "SELECT * FROM platform_dev_db_connect WHERE id = ?";

    private final JdbcTemplate primaryJdbcTemplate;

    public DefaultDataSourceDefinitionLoader(@Qualifier("primaryJdbcTemplate") JdbcTemplate primaryJdbcTemplate) {
        this.primaryJdbcTemplate = primaryJdbcTemplate;
    }

    @Override
    public List<Map<String, Object>> load(Dao dao) {
        if (primaryJdbcTemplate == null) {
            return Collections.emptyList();
        }
        return primaryJdbcTemplate.queryForList(SQL_CONNECT_LIST);
    }

    @Override
    public Map<String, Object> loadOne(String key) {
        if (primaryJdbcTemplate == null || key == null) {
            return null;
        }
        List<Map<String, Object>> rows = primaryJdbcTemplate.queryForList(SQL_CONNECT_ONE, key);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
