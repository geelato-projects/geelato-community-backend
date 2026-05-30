package cn.geelato.web.platform.boot;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync(proxyTargetClass = true)
public class EventAsyncConfiguration {
    @Bean(name = "eventExecutor")
    public Executor eventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(10);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("Event-Handler-");
        executor.setRejectedExecutionHandler((runnable, executor1) -> {
            if (!executor1.isShutdown()) {
                runnable.run();
            }
        });
        executor.initialize();
        return executor;
    }

    @Bean(name = "onlineUserExecutor")
    public Executor onlineUserExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(1000);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("OnlineUser-");
        executor.setRejectedExecutionHandler((runnable, executor1) -> {
            if (!executor1.isShutdown()) {
                runnable.run();
            }
        });
        executor.initialize();
        return executor;
    }

    @Bean(name = "resolveExecutor")
    public Executor resolveExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("Resolve-");
        executor.setRejectedExecutionHandler((runnable, executor1) -> {
            if (!executor1.isShutdown()) {
                runnable.run();
            }
        });
        executor.initialize();
        return executor;
    }
}
