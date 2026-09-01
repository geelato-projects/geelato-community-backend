package cn.geelato.mail.boot;

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

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Comparator;

/**
 * 邮件模块表初始化器：幂等建表（存在即跳过）。
 *
 * <p>对齐 app-scaffold-starter 的 {@code AppScaffoldSchemaInitializer} 模式（每表一个
 * SQL 文件、文件名即表名），脚本使用 {@code CREATE TABLE IF NOT EXISTS} 双重幂等。</p>
 *
 * <p><b>落位跟随 ORM 路由</b>：邮件实体声明 {@code @Entity(catalog="mail")}，表建在
 * 该实体实际路由到的数据源的默认库——</p>
 * <ul>
 *   <li>默认（未配置 catalog 映射）：路由解析为 null → 建在宿主主库（与 scaffold
 *       平台表同库），scaffold 应用零配置即可用；</li>
 *   <li>配置 {@code geelato.datasource.dynamic.catalog-mapping.mail=<connectId>}
 *       并注册对应数据源：邮件表整体迁至专属库（库的创建由部署方负责，本初始化器
 *       只建表不建库）。</li>
 * </ul>
 *
 * <p>SQL 脚本位置 {@code classpath*:geelato/mail/init/*.sql}，脚本内表名不加库名前缀，
 * 在所选连接的默认库上执行。</p>
 */
public class MailSchemaInitializer implements InitializingBean {

    private static final String INIT_SCRIPT_LOCATION = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX
            + "geelato/mail/init/*.sql";
    /** 代表实体：13 张邮件表共享 @Entity(catalog="mail")，路由解析一致 */
    private static final String REPRESENTATIVE_ENTITY = "mail_account";
    private static final Logger log = LoggerFactory.getLogger(MailSchemaInitializer.class);

    private final EntityDataSourceResolver entityDataSourceResolver;
    private final DynamicDataSourceRegistry dynamicDataSourceRegistry;

    public MailSchemaInitializer(EntityDataSourceResolver entityDataSourceResolver,
                                 DynamicDataSourceRegistry dynamicDataSourceRegistry) {
        this.entityDataSourceResolver = entityDataSourceResolver;
        this.dynamicDataSourceRegistry = dynamicDataSourceRegistry;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(INIT_SCRIPT_LOCATION);
        if (resources.length == 0) {
            throw new IllegalStateException("No mail init scripts found at " + INIT_SCRIPT_LOCATION);
        }
        Arrays.sort(resources, Comparator.comparing(this::resourceName));
        DataSourceHolder target = resolveTargetDataSource();
        try (Connection connection = target.connection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            for (Resource resource : resources) {
                String tableName = resolveTableName(resource);
                if (tableExists(metaData, catalog, tableName)) {
                    log.debug("Skip mail init script because table already exists: {}", tableName);
                    continue;
                }
                executeScript(connection, resource, tableName);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to initialize mail schema.", ex);
        }
        log.info("Mail schema initialized on datasource '{}' (catalog={})", target.key(), target.catalogName());
    }

    /** 解析邮件表实际路由的数据源：EntityDataSourceResolver 按 catalog 映射/dev_table 登记解析，null 回退主库 */
    private DataSourceHolder resolveTargetDataSource() throws SQLException {
        String key = entityDataSourceResolver.resolveDataSource(REPRESENTATIVE_ENTITY);
        javax.sql.DataSource dataSource = null;
        if (key != null) {
            dataSource = dynamicDataSourceRegistry.getDataSource(key);
            if (dataSource == null) {
                log.warn("Mail catalog resolved to datasource '{}' but not registered, fall back to primary", key);
            }
        }
        if (dataSource == null) {
            key = "primary";
            dataSource = dynamicDataSourceRegistry.getPrimaryDataSource();
        }
        Connection connection = dataSource.getConnection();
        return new DataSourceHolder(key, connection.getCatalog(), connection);
    }

    private void executeScript(Connection connection, Resource resource, String tableName) {
        try {
            log.info("Initialize mail table {} with script {}", tableName, resourceName(resource));
            ScriptUtils.executeSqlScript(connection, new EncodedResource(resource, "UTF-8"));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to execute mail init script: " + resourceName(resource), ex);
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

    /** 目标数据源描述（key + 连接默认库名 + 连接） */
    private record DataSourceHolder(String key, String catalogName, Connection connection) {
    }
}
