package cn.geelato.it.support.testcontainers;

public interface TestcontainersResource extends AutoCloseable {
    void start();

    @Override
    void close();
}
