package cn.geelato.it.support.testcontainers;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Objects;

public final class MysqlTestcontainersSupport implements TestcontainersResource {
    private final MySQLContainer<?> container;

    public MysqlTestcontainersSupport(MySQLContainer<?> container) {
        this.container = Objects.requireNonNull(container, "container");
    }

    public static MysqlTestcontainersSupport createDefault() {
        MySQLContainer<?> container = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.36"))
                .withDatabaseName("test")
                .withUsername("test")
                .withPassword("test");
        return new MysqlTestcontainersSupport(container);
    }

    public MySQLContainer<?> container() {
        return container;
    }

    @Override
    public void start() {
        container.start();
    }

    @Override
    public void close() {
        container.stop();
    }

    public String jdbcUrl() {
        return container.getJdbcUrl();
    }

    public String username() {
        return container.getUsername();
    }

    public String password() {
        return container.getPassword();
    }
}
