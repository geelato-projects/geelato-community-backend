package cn.geelato.web.platform.boot;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
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
}
