package cn.geelato.web.runtime.quickstart;

import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.annotation.ApiRuntimeRestController;
import cn.geelato.web.platform.annotation.RuntimeMapping;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ApiPrefixAutoConfiguration implements WebMvcConfigurer {
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/api/runtime", c -> c.isAnnotationPresent(ApiRuntimeRestController.class));
    }
}
