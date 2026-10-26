package cn.geelato.script.kit.sql;

import cn.geelato.core.ds.DataSourceManager;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.utils.LocalBoundedCache;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Optional;

/**
 * 默认解析实现：表名 → MetaManager 实体登记（platform_dev_table.connect_id / @Entity(connectId) / catalog 映射）
 * → connectId → 连接池 URL 或 platform_dev_db_connect 配置行定位实例与库名。
 * 同名表登记到多个连接时 MetaManager 本身 last-wins，与 ORM 路由行为一致，不做歧义检测。
 */
public class MetaTableLocationResolver implements TableLocationResolver {

    private static final LocalBoundedCache<String, Optional<TableLocation>> CACHE =
            new LocalBoundedCache<>("script-table-location", 60_000L, 4096);

    @Override
    public TableLocation resolve(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return null;
        }
        String key = tableName.toLowerCase();
        if (CACHE.exists(key)) {
            return CACHE.get(key).orElse(null);
        }
        TableLocation location = resolveUncached(tableName);
        CACHE.put(key, Optional.ofNullable(location));
        return location;
    }

    private TableLocation resolveUncached(String tableName) {
        MetaManager metaManager = MetaManager.singleInstance();
        EntityMeta exact = null;
        EntityMeta insensitive = null;
        for (EntityMeta candidate : metaManager.getAll()) {
            String name = candidate.getTableName();
            if (name == null) {
                continue;
            }
            if (name.equals(tableName)) {
                exact = candidate;
                break;
            }
            if (insensitive == null && name.equalsIgnoreCase(tableName)) {
                insensitive = candidate;
            }
        }
        EntityMeta meta = exact != null ? exact : insensitive;
        if (meta == null) {
            return null;
        }
        String connectId = metaManager.resolveConnectId(meta.getEntityName());
        if (connectId == null || connectId.isBlank()) {
            connectId = DataSourceManager.singleInstance().getDefaultDataSourceKey();
        }
        if (connectId == null || connectId.isBlank()) {
            connectId = "primary";
        }
        return locate(connectId, tableName);
    }

    private TableLocation locate(String connectId, String tableName) {
        DataSourceManager manager = DataSourceManager.singleInstance();
        DataSource pool = manager.getRegisteredDataSource(connectId);
        ExecutingTarget fromPool = pool == null ? null : ExecutingTarget.of(pool);
        Map<String, Object> config = manager.getDataSourceConfig(connectId);

        String dbType = fromPool == null ? null : fromPool.dbType();
        String host = fromPool == null ? null : fromPool.host();
        Integer port = fromPool == null ? null : fromPool.port();
        String catalog = fromPool == null ? null : fromPool.db();
        String schema = null;
        if (config != null) {
            dbType = orDefault(str(config.get("db_type")), dbType);
            host = orDefault(str(config.get("db_hostname_ip")), host);
            String configPort = str(config.get("db_port"));
            if (port == null && configPort != null) {
                try {
                    port = Integer.valueOf(configPort.trim());
                } catch (NumberFormatException ignored) {
                }
            }
            catalog = orDefault(str(config.get("db_name")), catalog);
            schema = str(config.get("db_schema"));
        }
        if (dbType == null && host == null && catalog == null) {
            throw new IllegalArgumentException("跨库改写定位失败: 表 " + tableName + " 登记的连接 " + connectId
                    + " 既无连接池也无 platform_dev_db_connect 配置（登记与配置不一致，请检查连接登记）");
        }
        String qualifier;
        if ("postgresql".equalsIgnoreCase(dbType) || "postgres".equalsIgnoreCase(dbType)) {
            qualifier = schema == null || schema.isBlank() ? "public" : schema;
        } else {
            qualifier = catalog;
        }
        if (qualifier == null || qualifier.isBlank()) {
            throw new IllegalArgumentException("跨库改写定位失败: 连接 " + connectId
                    + " 缺少 db_name（PG 缺少 db_schema），无法确定表 " + tableName + " 所在库");
        }
        return new TableLocation(connectId, catalog, qualifier, host, port, dbType);
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String str(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }
}
