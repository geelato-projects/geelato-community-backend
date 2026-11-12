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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


@Getter
@Slf4j
public class DataSourceManager extends AbstractManager {


    private static DataSourceManager instance;

    private final static ConcurrentHashMap<String, DataSource> dataSourceMap = new ConcurrentHashMap<>();

    private final static ConcurrentHashMap<String, Map<String, Object>> dataSourceConfigMap = new ConcurrentHashMap<>();

    /** loadOne 确认不存在的 connectId，避免坏 id 反复打库 */
    private final static Set<String> absentConnectIds = ConcurrentHashMap.newKeySet();

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
        Map<String, Object> config = resolveConfig(connectId);
        if (config == null) {
            return null;
        }
        Object dbType = config.get("db_type");
        return dbType == null ? null : dbType.toString();
    }

    /**
     * 连接配置只读访问（platform_dev_db_connect 行：db_type/db_hostname_ip/db_port/db_name/db_schema 等），
     * 供跨库改写等能力定位连接的实例与库名。
     *
     * @param connectId 连接 id
     * @return 配置行副本，未登记返回 null
     */
    public Map<String, Object> getDataSourceConfig(String connectId) {
        Map<String, Object> config = resolveConfig(connectId);
        return config == null ? null : Map.copyOf(config);
    }

    /**
     * 快照 miss 时经 definitionLoader.loadOne 实时补载（对齐 orm Registry 的运行期查库行为），
     * 使运行期新建的连接（如物化视图引擎连接）对 dbType 解析立即可见。
     */
    private Map<String, Object> resolveConfig(String connectId) {
        Map<String, Object> config = connectId == null ? null : dataSourceConfigMap.get(connectId);
        if (config != null || connectId == null) {
            return config;
        }
        DataSourceDefinitionLoader loader = definitionLoader;
        if (loader == null || absentConnectIds.contains(connectId)) {
            return null;
        }
        try {
            Map<String, Object> row = loader.loadOne(connectId);
            if (row == null) {
                absentConnectIds.add(connectId);
                return null;
            }
            dataSourceConfigMap.put(connectId, row);
            return row;
        } catch (Exception e) {
            log.warn("lazy load db connect {} failed", connectId, e);
            return null;
        }
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
