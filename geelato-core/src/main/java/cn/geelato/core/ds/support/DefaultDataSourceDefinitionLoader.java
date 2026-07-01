package cn.geelato.core.ds.support;

import cn.geelato.core.ds.spi.DataSourceDefinitionLoader;
import cn.geelato.core.orm.Dao;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 当前平台表结构下的默认数据源定义加载器。
 */
public class DefaultDataSourceDefinitionLoader implements DataSourceDefinitionLoader {

    private static final String SQL_CONNECT_LIST = "SELECT * FROM platform_dev_db_connect";

    @Override
    public List<Map<String, Object>> load(Dao dao) {
        if (dao == null || dao.getJdbcTemplate() == null) {
            return Collections.emptyList();
        }
        return dao.getJdbcTemplate().queryForList(SQL_CONNECT_LIST);
    }
}
