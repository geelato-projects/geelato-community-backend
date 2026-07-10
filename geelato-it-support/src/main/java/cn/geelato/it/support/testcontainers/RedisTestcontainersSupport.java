package cn.geelato.it.support.testcontainers;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Objects;

public final class RedisTestcontainersSupport implements TestcontainersResource {
    private static final int REDIS_PORT = 6379;

    private final GenericContainer<?> container;

    public RedisTestcontainersSupport(GenericContainer<?> container) {
        this.container = Objects.requireNonNull(container, "container");
    }

    public static RedisTestcontainersSupport createDefault() {
        GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
                .withExposedPorts(REDIS_PORT);
        return new RedisTestcontainersSupport(container);
    }

    public GenericContainer<?> container() {
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

    public String host() {
        return container.getHost();
    }

    public int port() {
        return container.getMappedPort(REDIS_PORT);
    }

    public String redisUrl() {
        return "redis://" + host() + ":" + port();
    }
}
