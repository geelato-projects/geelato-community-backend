package cn.geelato.archive.engine.boot;

import cn.geelato.datasource.DynamicDataSourceRegistry;
import cn.geelato.datasource.EntityDataSourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Comparator;

/**
 * 归档模块表初始化器：幂等建表（存在即跳过），照 MailSchemaInitializer 模式。
 * <p>SQL 脚本位于 {@code classpath*:geelato/archive/init/*.sql}（每表一文件、文件名即表名，
 * CREATE TABLE IF NOT EXISTS 双重幂等）。落位跟随 ORM 路由：归档实体声明
 * {@code @Entity(catalog="platform")}，未配置 catalog 映射时建在宿主主库。</p>
 */
public class ArchiveSchemaInitializer implements InitializingBean {

    private static final String INIT_SCRIPT_LOCATION = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX
            + "geelato/archive/init/*.sql";
    /** 代表实体：两张归档表共享 catalog 路由 */
    private static final String REPRESENTATIVE_ENTITY = "platform_archive_policy";
    private static final Logger log = LoggerFactory.getLogger(ArchiveSchemaInitializer.class);

    private final EntityDataSourceResolver entityDataSourceResolver;
    private final DynamicDataSourceRegistry dynamicDataSourceRegistry;

    public ArchiveSchemaInitializer(EntityDataSourceResolver entityDataSourceResolver,
                                    DynamicDataSourceRegistry dynamicDataSourceRegistry) {
        this.entityDataSourceResolver = entityDataSourceResolver;
        this.dynamicDataSourceRegistry = dynamicDataSourceRegistry;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(INIT_SCRIPT_LOCATION);
        if (resources.length == 0) {
            throw new IllegalStateException("No archive init scripts found at " + INIT_SCRIPT_LOCATION);
        }
        Arrays.sort(resources, Comparator.comparing(this::resourceName));
        DataSource dataSource = resolveTargetDataSource();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            for (Resource resource : resources) {
                String tableName = resolveTableName(resource);
                if (tableExists(metaData, catalog, tableName)) {
                    log.debug("Skip archive init script because table already exists: {}", tableName);
                    continue;
                }
                executeScript(connection, resource, tableName);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to initialize archive schema.", ex);
        }
        log.info("Archive schema initialized (catalog follows entity routing of {})", REPRESENTATIVE_ENTITY);
    }

    private DataSource resolveTargetDataSource() throws SQLException {
        String key = entityDataSourceResolver.resolveDataSource(REPRESENTATIVE_ENTITY);
        DataSource dataSource = null;
        if (key != null) {
            dataSource = dynamicDataSourceRegistry.getDataSource(key);
            if (dataSource == null) {
                log.warn("Archive catalog resolved to datasource '{}' but not registered, fall back to primary", key);
            }
        }
        if (dataSource == null) {
            dataSource = dynamicDataSourceRegistry.getPrimaryDataSource();
        }
        return dataSource;
    }

    private void executeScript(Connection connection, Resource resource, String tableName) {
        try {
            log.info("Initialize archive table {} with script {}", tableName, resourceName(resource));
            ScriptUtils.executeSqlScript(connection, new EncodedResource(resource, "UTF-8"));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to execute archive init script: " + resourceName(resource), ex);
        }
    }

    private boolean tableExists(DatabaseMetaData metaData, String catalog, String tableName) throws SQLException {
        return tableExists(metaData, catalog, tableName, "TABLE")
                || tableExists(metaData, catalog, tableName.toUpperCase(), "TABLE")
                || tableExists(metaData, catalog, tableName.toLowerCase(), "TABLE");
    }

    private boolean tableExists(DatabaseMetaData metaData, String catalog, String tableName, String type) throws SQLException {
        try (ResultSet tables = metaData.getTables(catalog, null, tableName, new String[]{type})) {
            return tables.next();
        }
    }

    private String resolveTableName(Resource resource) {
        String filename = resourceName(resource);
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
    }

    private String resourceName(Resource resource) {
        return resource.getFilename() == null ? resource.getDescription() : resource.getFilename();
    }
}
