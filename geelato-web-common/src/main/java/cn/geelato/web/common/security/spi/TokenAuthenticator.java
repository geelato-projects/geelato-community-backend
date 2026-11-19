package cn.geelato.web.common.security.spi;

import java.util.Collection;

/**
 * 令牌认证器 SPI：一种 Authorization 请求头方案（如 JWTBearer / Anonymous / SystemToken / Bearer）
 * 对应一个实现。负责「凭证校验 + 用户解析」，产出 {@link TokenAuthentication}；
 * SecurityContext 填充、Shiro 会话建立、上下文缓存、在线打点等统一由
 * {@code DefaultSecurityInterceptor} 完成——实现类不得引入任何 Shiro 类型。
 * <p>
 * 扩展方式：实现本接口并注册为 Spring Bean（{@code @Component} 或 {@code @Bean}），
 * 即被 {@link TokenAuthenticatorRegistry} 自动发现并参与前缀分发，无需改动拦截器。
 * 新方案建议使用不与既有前缀冲突的自定义前缀（如 {@code DingtalkCode }，注意尾随空格）。
 */
public interface TokenAuthenticator {

    /**
     * 本认证器接管的 Authorization 头前缀集合（带尾随空格，如 {@code "JWTBearer "}）。
     * 各认证器之间前缀必须互斥，注册期重复即启动失败。
     */
    Collection<String> schemePrefixes();

    /** 请求头值是否以本认证器的前缀开头。 */
    default boolean supports(String rawToken) {
        for (String prefix : schemePrefixes()) {
            if (rawToken.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 方法级访问门禁，在认证结果缓存命中短路之前执行——防止借缓存绕过
     * （如 SystemToken 的 {@code @AllowSystemAccess} 方法限制）。默认无门禁。
     */
    default void preAuthCheck(TokenAuthenticationContext ctx) {
    }

    /**
     * 校验凭证并解析出会话用户。失败抛 {@link cn.geelato.web.common.interceptor.UnauthorizedException}
     * （携带根因），由拦截器统一转为 401；不要吞异常返回 null。
     */
    TokenAuthentication authenticate(TokenAuthenticationContext ctx);
}
