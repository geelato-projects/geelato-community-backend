package cn.geelato.web.platform.boot;

import cn.geelato.web.common.interceptor.*;
import jakarta.annotation.Resource;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author geemeta
 */
@Configuration
// todo：servlet interceptor, does not work under spring webflux，wait refactor
public class InterceptorConfiguration extends BaseConfiguration implements WebMvcConfigurer {
    @Resource
    private OAuthConfigurationProperties oAuthConfigurationProperties;

    private static final String urlPrefix = "/api";

    @Override
    public void addInterceptors(@NotNull InterceptorRegistry registry) {
        registry.addInterceptor(new DefaultSecurityInterceptor(oAuthConfigurationProperties))
                .addPathPatterns("/**")
                // 以下为排除鉴权的路径
                // 登录接口
                .excludePathPatterns(urlPrefix + "/oauth2/login")
                // 静态资源
                .excludePathPatterns("/assets/**")
                // 错误页面
                .excludePathPatterns("/error/**")
                // swagger-ui 相关
                .excludePathPatterns("/v3/**")
                .excludePathPatterns("/swagger-ui/index.html")
                // 重置密码接口
                .excludePathPatterns(urlPrefix + "/user/forgetValid")
                .excludePathPatterns(urlPrefix + "/user/forget")
                .excludePathPatterns(urlPrefix + "/code/generate/**")
                // 未登录前相关配置文件
                .excludePathPatterns(urlPrefix + "/resources/json")
                // 加载或下载文件
                .excludePathPatterns(urlPrefix + "/resources/file")
                // 微信回调接口
                .excludePathPatterns("/wx/callback/hook")
                // 微信登录接口
                .excludePathPatterns("/wx/login/**")
                // 微信重定向接口
                .excludePathPatterns("/wx/redirect")
                // oauth2登录接口
                .excludePathPatterns("/oauth2/**")
        ;
        registry.addInterceptor(new DataSourceInterceptor()).addPathPatterns("/**");
        registry.addInterceptor(new CacheInterceptor()).addPathPatterns("/**");
    }
}
