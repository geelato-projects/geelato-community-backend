package cn.geelato.mcp.platform;

import org.junit.jupiter.api.Assumptions;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

public abstract class McpPlatformMysqlContainerSupport {
    private static MySQLContainer<?> mysql;

    protected static synchronized void assumeMysqlStarted() {
        Assumptions.assumeTrue(isDockerAvailable(), "docker not available, skip");
        try {
            if (mysql == null) {
                mysql = new MySQLContainer<>("mysql:8.0.36")
                        .withDatabaseName("geelato")
                        .withUsername("geelato")
                        .withPassword("geelato");
            }
            if (!mysql.isRunning()) {
                mysql.start();
            }
        } catch (Throwable t) {
            Assumptions.assumeTrue(false, "mysql container start failed, skip: " + t.getMessage());
        }
    }

    protected static synchronized void stopMysqlIfStarted() {
        if (mysql != null) {
            try {
                mysql.stop();
            } catch (Exception ignored) {
            } finally {
                mysql = null;
            }
        }
    }

    protected static synchronized String[] mysqlDatasourceArgs() {
        if (mysql == null || !mysql.isRunning()) {
            return new String[0];
        }
        return new String[]{
                "--spring.datasource.primary.jdbc-url=" + mysql.getJdbcUrl(),
                "--spring.datasource.primary.username=" + mysql.getUsername(),
                "--spring.datasource.primary.password=" + mysql.getPassword(),
                "--spring.datasource.primary.driver-class-name=" + mysql.getDriverClassName()
        };
    }

    private static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ignored) {
            return false;
        }
    }
}

