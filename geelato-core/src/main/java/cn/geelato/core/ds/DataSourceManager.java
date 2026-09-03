package cn.geelato.core.ds;

import cn.geelato.core.ds.spi.DataSourceDefinitionLoader;
import cn.geelato.core.AbstractManager;
import cn.geelato.core.orm.Dao;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Getter
@Slf4j
public class DataSourceManager extends AbstractManager {


    private static DataSourceManager instance;

    private final static ConcurrentHashMap<String, DataSource> dataSourceMap = new ConcurrentHashMap<>();

    private final static ConcurrentHashMap<String, Map<String, Object>> dataSourceConfigMap = new ConcurrentHashMap<>();

    @Setter
    private volatile String defaultDataSourceKey;
    private DataSourceDefinitionLoader definitionLoader = null;

    public static DataSourceManager singleInstance() {
        lock.lock();
        if (instance == null) {
            instance = new DataSourceManager();
        }
        lock.unlock();
        return instance;
    }

    private DataSourceManager() {
        log.info("DataSourceManager Instancing...");
    }


    public void parseDataSourceMeta(Dao dao) {
        if (dao.getJdbcTemplate().getDataSource() != null) {
            dataSourceMap.put("primary", dao.getJdbcTemplate().getDataSource());
        }
        if (definitionLoader == null) {
            return;
        }
        List<Map<String, Object>> dbConnectList = definitionLoader.load(dao);
        for (Map<String, Object> dbConnectMap : dbConnectList) {
            String connectId = dbConnectMap.get("id").toString();
            dataSourceConfigMap.put(connectId, dbConnectMap);
        }
    }

    public DataSource getRegisteredDataSource(String key) {
        return key == null ? null : dataSourceMap.get(key);
    }

    public String getDataSourceDbType(String connectId) {
        Map<String, Object> config = connectId == null ? null : dataSourceConfigMap.get(connectId);
        if (config == null) {
            return null;
        }
        Object dbType = config.get("db_type");
        return dbType == null ? null : dbType.toString();
    }

    public void registerDataSource(String key, DataSource dataSource) {
        if (key == null || key.isBlank() || dataSource == null) {
            return;
        }
        dataSourceMap.put(key, dataSource);
    }

    public void setDefinitionLoader(DataSourceDefinitionLoader definitionLoader) {
        if (definitionLoader != null) {
            this.definitionLoader = definitionLoader;
        }
    }
}
