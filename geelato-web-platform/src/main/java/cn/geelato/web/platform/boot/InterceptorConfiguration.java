package cn.geelato.web.platform.boot;

import cn.geelato.web.platform.interceptor.CacheInterceptor;
import cn.geelato.web.platform.interceptor.DataSourceInterceptor;
import cn.geelato.web.platform.interceptor.JWTInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author geemeta
 */
@Configuration
public class InterceptorConfiguration extends BaseConfiguration implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new JWTInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns("/iam/**")
                .excludePathPatterns("/assets/**")
                .excludePathPatterns("/error/**")
                .excludePathPatterns("/v3/**")
                .excludePathPatterns("/api/user/forgetValid")
                .excludePathPatterns("/api/user/forget")
                .excludePathPatterns("/api/code/generate/**")
                .excludePathPatterns("/swagger-ui/index.html")
                .excludePathPatterns("/api/config")
                .excludePathPatterns("/api/resources/**")
                .excludePathPatterns("/api/sys/config/getValue/**")
                ;
        registry.addInterceptor(new DataSourceInterceptor()).addPathPatterns("/**");
        registry.addInterceptor(new CacheInterceptor()).addPathPatterns("/**");
    }
}
