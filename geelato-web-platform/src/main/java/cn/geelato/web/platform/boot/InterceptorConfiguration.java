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

    @Autowired(required = false)
    private cn.geelato.web.common.security.delegate.DelegateSessionStore delegateSessionStore;

    @Override
    public void addInterceptors(@NotNull InterceptorRegistry registry) {
        DefaultSecurityInterceptor securityInterceptor = new DefaultSecurityInterceptor(oAuthConfigurationProperties, orgProvider, userProvider);
        securityInterceptor.setOnlineUserTracker(onlineUserTracker);
        securityInterceptor.setTrafficColoringProperties(trafficColoringProperties);
        securityInterceptor.setTrafficTagStrategy(trafficTagStrategy);
        securityInterceptor.setDelegateSessionStore(delegateSessionStore);

        java.util.List<String> excludes = securityInterceptorProperties.resolveEffectiveExcludes();

        registry.addInterceptor(securityInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(excludes);
        registry.addInterceptor(apiRestControllerInvokeLogging)
                .addPathPatterns("/**");
    }
}
