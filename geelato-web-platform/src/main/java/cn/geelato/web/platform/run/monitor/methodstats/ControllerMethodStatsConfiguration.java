package cn.geelato.web.platform.run.monitor.methodstats;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Controller 方法调用统计装配。
 */
@Configuration
public class ControllerMethodStatsConfiguration implements WebMvcConfigurer {

    @Autowired
    private ControllerMethodCallStats stats;

    @Override
    public void addInterceptors(@NotNull InterceptorRegistry registry) {
        registry.addInterceptor(new ControllerMethodStatsInterceptor(stats))
                .addPathPatterns("/**")
                // order 最低优先级，排在 DefaultSecurityInterceptor 之后：仅统计通过鉴权、即将执行的调用
                .order(Ordered.LOWEST_PRECEDENCE);
    }
}
