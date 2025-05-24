package cn.geelato.web.quickstart;

import cn.geelato.web.common.annotation.ApiRestController;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ApiPrefixAutoConfiguration implements WebMvcConfigurer {
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/api",  handlerType ->
                MergedAnnotations.from(
                        handlerType,
                        MergedAnnotations.SearchStrategy.TYPE_HIERARCHY
                ).isPresent(ApiRestController.class));
    }
}
