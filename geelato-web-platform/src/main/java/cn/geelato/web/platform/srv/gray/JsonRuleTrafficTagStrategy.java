package cn.geelato.web.platform.srv.gray;

import cn.geelato.security.User;
import cn.geelato.traffic.TrafficTagStrategy;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 基于内存 JSON 规则的流量染色策略，通过 {@code @Primary} 覆盖
 * {@link cn.geelato.web.common.traffic.WhitelistTrafficTagStrategy}。
 * <p>
 * 由 {@link cn.geelato.web.platform.boot.InterceptorConfiguration} 通过
 * {@code @Autowired(required=false) TrafficTagStrategy} 自动注入并接入
 * {@link cn.geelato.web.common.interceptor.DefaultSecurityInterceptor}，无需改动拦截器。
 */
@Component
@Primary
public class JsonRuleTrafficTagStrategy implements TrafficTagStrategy {

    private final GrayRuleMatcher matcher;

    public JsonRuleTrafficTagStrategy(GrayRuleMatcher matcher) {
        this.matcher = matcher;
    }

    @Override
    public String resolveTag(HttpServletRequest request, User user) {
        return matcher.resolve(user);
    }
}
