package cn.geelato.app.scaffold.boot;

import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.common.annotation.ApiRuntimeRestController;
import cn.geelato.web.common.annotation.DesignTimeApiRestController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration
public class AppScaffoldApiPrefixAutoConfiguration implements WebMvcConfigurer {
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/api", handlerType ->
                AnnotatedElementUtils.findMergedAnnotation(handlerType, ApiRuntimeRestController.class) != null
                        || AnnotatedElementUtils.findMergedAnnotation(handlerType, DesignTimeApiRestController.class) != null
                        || AnnotatedElementUtils.findMergedAnnotation(handlerType, ApiRestController.class) != null);
    }
}
