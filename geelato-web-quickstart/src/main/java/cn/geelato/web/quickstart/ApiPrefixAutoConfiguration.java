package cn.geelato.web.quickstart;

import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.annotation.ApiRuntimeRestController;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ApiPrefixAutoConfiguration implements WebMvcConfigurer {
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/api", c ->
                c.isAnnotationPresent(ApiRestController.class)
                        || c.isAnnotationPresent(ApiRuntimeRestController.class));
    }
}
