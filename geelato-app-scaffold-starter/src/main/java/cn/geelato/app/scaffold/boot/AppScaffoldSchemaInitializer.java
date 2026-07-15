package cn.geelato.app.scaffold.boot;

import cn.geelato.core.orm.Dao;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Comparator;

public class AppScaffoldSchemaInitializer implements InitializingBean {

    private static final String INIT_SCRIPT_LOCATION = "classpath*:geelato/app/scaffold/init/*.sql";
    private static final Logger log = LoggerFactory.getLogger(AppScaffoldSchemaInitializer.class);

    private final Dao dao;

    public AppScaffoldSchemaInitializer(Dao dao) {
        this.dao = dao;
    }

    @Override
    public void afterPropertiesSet() {
        ResourcePatternResolver resolver = new org.springframework.core.io.support.PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources(INIT_SCRIPT_LOCATION);
            if (resources.length == 0) {
                throw new IllegalStateException("No scaffold init scripts found at " + INIT_SCRIPT_LOCATION);
            }
            Arrays.sort(resources, Comparator.comparing(this::resourceName));
            try (Connection connection = dao.getJdbcTemplate().getDataSource().getConnection()) {
                DatabaseMetaData metaData = connection.getMetaData();
                String catalog = connection.getCatalog();
                String schema = connection.getSchema();
                for (Resource resource : resources) {
                    String tableName = resolveTableName(resource);
                    if (tableExists(metaData, catalog, schema, tableName)) {
                        log.info("Skip scaffold init script because table already exists: {}", tableName);
                        continue;
                    }
                    executeScript(connection, resource, tableName);
                }
            }
        } catch (IOException | SQLException ex) {
            throw new IllegalStateException("Failed to initialize scaffold schema.", ex);
        }
    }

    private void executeScript(Connection connection, Resource resource, String tableName) {
        try {
            log.info("Initialize scaffold table {} with script {}", tableName, resourceName(resource));
            ScriptUtils.executeSqlScript(connection, new EncodedResource(resource, "UTF-8"));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to execute scaffold init script: " + resourceName(resource), ex);
        }
    }

    private boolean tableExists(DatabaseMetaData metaData, String catalog, String schema, String tableName) throws SQLException {
        return tableExists(metaData, catalog, schema, tableName, "TABLE")
                || tableExists(metaData, catalog, schema, tableName.toUpperCase(), "TABLE")
                || tableExists(metaData, catalog, schema, tableName.toLowerCase(), "TABLE");
    }

    private boolean tableExists(DatabaseMetaData metaData, String catalog, String schema, String tableName, String type) throws SQLException {
        try (ResultSet tables = metaData.getTables(catalog, schema, tableName, new String[]{type})) {
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
