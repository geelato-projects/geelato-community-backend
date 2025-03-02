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
                // 以下为排除鉴权的路径
                // 登录接口
                .excludePathPatterns("/iam/**")
                // 静态资源
                .excludePathPatterns("/assets/**")
                // 错误页面
                .excludePathPatterns("/error/**")
                // swagger-ui 相关
                .excludePathPatterns("/v3/**")
                .excludePathPatterns("/swagger-ui/index.html")
                // 重置密码接口
                .excludePathPatterns("/api/user/forgetValid")
                .excludePathPatterns("/api/user/forget")
                .excludePathPatterns("/api/code/generate/**")
                // 未登录前相关配置文件
                .excludePathPatterns("/api/resources/json")
                // 加载或下载文件
                .excludePathPatterns("/api/resources/file")
                // 微信回调接口
                .excludePathPatterns("/wx/callback/hook")
                // 微信登录接口
                .excludePathPatterns("/wx/login/**")
                // 微信重定向接口
                .excludePathPatterns("/wx/redirect")
        ;
        registry.addInterceptor(new DataSourceInterceptor()).addPathPatterns("/**");
        registry.addInterceptor(new CacheInterceptor()).addPathPatterns("/**");
    }
}
