package cn.geelato.web.common.interceptor.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * rest方法中加了该注解，表示允许外部系统以固定令牌调用：
 * 请求头 {@code Authorization: SystemToken <固定密钥>}（密钥见
 * {@code geelato.security.system-token.token} 配置）。
 * <p>
 * 适用于既能给前端（用户 token）调用、也能给不具备用户 token 的外部系统
 * （如 dyn 模块发送站内信）调用的接口。固定令牌认证通过后以系统主体
 * （systemPrincipal，userId=system）身份运行，不影响前端用户的正常认证。
 * 未加该注解的方法，固定令牌一律 401。
 *
 * @author geemeta
 */
@Target({METHOD})
@Retention(RUNTIME)
public @interface AllowSystemAccess {
}
