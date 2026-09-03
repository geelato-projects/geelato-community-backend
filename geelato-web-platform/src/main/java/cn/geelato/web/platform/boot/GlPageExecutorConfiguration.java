package cn.geelato.web.platform.boot;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 页面读接口并行查询线程池（glPageExecutor）。
 *
 * 用于 getPageAndCustom 等运行时接口的多段数据（pageLang/pageCustom/pagePerms）并行组装，
 * 替代原先的串行 DB 往返。池满时退化为调用方线程执行（CallerRuns 语义），不丢请求、不抛拒绝异常。
 *
 * 注意：@Configuration 类自身也会注册为同名 Bean，因此类名不能与 @Bean 方法名相同，
 * 否则触发 Bean 定义冲突（spring.main.allow-bean-definition-overriding 默认关闭）。
 */
@Configuration
public class GlPageExecutorConfiguration {

    @Bean(name = "glPageExecutor")
    public Executor glPageExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("gl-page-");
        executor.setRejectedExecutionHandler((runnable, executor1) -> {
            if (!executor1.isShutdown()) {
                runnable.run();
            }
        });
        executor.initialize();
        return executor;
    }
}
