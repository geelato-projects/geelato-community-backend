package cn.geelato.web.platform.boot;

import cn.geelato.web.common.interceptor.*;
import cn.geelato.web.common.online.OnlineUserTracker;
import cn.geelato.web.common.traffic.TrafficColoringProperties;
import cn.geelato.traffic.TrafficTagStrategy;
import cn.geelato.web.platform.logging.web.ApiRestControllerInvokeLogging;
import jakarta.annotation.Resource;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author geemeta
 */
@Configuration
public class InterceptorConfiguration extends BaseConfiguration implements WebMvcConfigurer {
    @Resource
    private OAuthConfigurationProperties oAuthConfigurationProperties;
    @Resource
    private ApiRestControllerInvokeLogging apiRestControllerInvokeLogging;

    @Autowired
    private cn.geelato.security.OrgProvider orgProvider;
    @Autowired
    private cn.geelato.security.UserProvider userProvider;

    @Autowired(required = false)
    @Qualifier("asyncOnlineUserTracker")
    private OnlineUserTracker onlineUserTracker;

    @Autowired(required = false)
    private TrafficColoringProperties trafficColoringProperties;

    @Autowired(required = false)
    private TrafficTagStrategy trafficTagStrategy;

    private static final String urlPrefix = "/api";

    @Override
    public void addInterceptors(@NotNull InterceptorRegistry registry) {
        DefaultSecurityInterceptor securityInterceptor = new DefaultSecurityInterceptor(oAuthConfigurationProperties, orgProvider, userProvider);
        securityInterceptor.setOnlineUserTracker(onlineUserTracker);
        securityInterceptor.setTrafficColoringProperties(trafficColoringProperties);
        securityInterceptor.setTrafficTagStrategy(trafficTagStrategy);

        registry.addInterceptor(securityInterceptor)
                .addPathPatterns("/**")
                // 以下为排除鉴权的路径
                // 登录接口
                .excludePathPatterns(urlPrefix + "/oauth2/login")
                .excludePathPatterns(urlPrefix + "/oauth2/refreshToken")
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
                // 分支识别
                .excludePathPatterns(urlPrefix + "/branch")
                // 微信回调接口
                .excludePathPatterns("/wx/callback/hook")
                // 微信登录接口
                .excludePathPatterns("/wx/login/**")
                // 微信重定向接口
                .excludePathPatterns("/wx/redirect")
                // oauth2登录接口
                .excludePathPatterns("/oauth2/**")
                // 监控页面
                .excludePathPatterns("/monitor/**")
                .excludePathPatterns("/wx/validate/**")
        ;
        registry.addInterceptor(apiRestControllerInvokeLogging)
                .addPathPatterns("/**");
    }
}
