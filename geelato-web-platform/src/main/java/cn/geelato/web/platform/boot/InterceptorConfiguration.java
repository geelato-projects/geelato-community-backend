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

    @Autowired
    private cn.geelato.web.common.interceptor.SecurityInterceptorProperties securityInterceptorProperties;

    @Override
    public void addInterceptors(@NotNull InterceptorRegistry registry) {
        DefaultSecurityInterceptor securityInterceptor = new DefaultSecurityInterceptor(oAuthConfigurationProperties, orgProvider, userProvider);
        securityInterceptor.setOnlineUserTracker(onlineUserTracker);
        securityInterceptor.setTrafficColoringProperties(trafficColoringProperties);
        securityInterceptor.setTrafficTagStrategy(trafficTagStrategy);

        // 排除鉴权的路径：内置默认（SecurityInterceptorProperties.initDefaultExcludes）
        // + 用户配置追加（geelato.security.interceptor.extra-excludes[*]）
        // + 用户配置覆盖（geelato.security.interceptor.default-excludes[*] + override-default-excludes）
        java.util.List<String> excludes = securityInterceptorProperties.resolveEffectiveExcludes();

        registry.addInterceptor(securityInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(excludes);
        registry.addInterceptor(apiRestControllerInvokeLogging)
                .addPathPatterns("/**");
    }
}
