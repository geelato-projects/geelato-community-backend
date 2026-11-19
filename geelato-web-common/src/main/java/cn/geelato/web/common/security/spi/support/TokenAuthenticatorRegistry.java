package cn.geelato.web.common.security.spi.support;

import cn.geelato.web.common.interceptor.UnauthorizedException;
import cn.geelato.web.common.security.spi.TokenAuthenticator;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证器注册中心：按 Authorization 头前缀路由到对应 {@link TokenAuthenticator}。
 * <p>
 * 容器内所有认证器 bean 经构造注入自动注册；上层扩展只需实现接口并注册为
 * Spring Bean。前缀互斥在启动期强校验，重复即失败；运行期无匹配前缀直接 401，
 * 不做任何兜底。
 */
@Component
public class TokenAuthenticatorRegistry {

    private final Map<String, TokenAuthenticator> byPrefix = new HashMap<>();

    public TokenAuthenticatorRegistry(List<TokenAuthenticator> authenticators) {
        for (TokenAuthenticator authenticator : authenticators) {
            register(authenticator);
        }
    }

    /** 追加认证器（供测试或非常规装配使用）。前缀与既有冲突即抛出。 */
    public synchronized void register(TokenAuthenticator authenticator) {
        for (String prefix : authenticator.schemePrefixes()) {
            TokenAuthenticator existed = byPrefix.put(prefix, authenticator);
            if (existed != null && existed != authenticator) {
                throw new IllegalStateException(
                        "认证方案前缀重复注册: \"" + prefix + "\" 已由 " + existed.getClass().getName()
                                + " 注册，冲突实现 " + authenticator.getClass().getName());
            }
        }
    }

    public TokenAuthenticator resolve(String rawToken) {
        for (Map.Entry<String, TokenAuthenticator> entry : byPrefix.entrySet()) {
            if (rawToken.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        throw new UnauthorizedException("不支持的认证方案: " + schemeOf(rawToken));
    }

    private String schemeOf(String rawToken) {
        int spaceIdx = rawToken.indexOf(' ');
        return spaceIdx > 0 ? rawToken.substring(0, spaceIdx) : rawToken;
    }
}
