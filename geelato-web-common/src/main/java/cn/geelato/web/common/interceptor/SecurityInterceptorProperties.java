package cn.geelato.web.common.interceptor;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 安全拦截器（{@code DefaultSecurityInterceptor}）的路径排除配置。
 * <p>
 * 实际生效的排除路径 = {@link #defaultExcludes}（内置默认）+ {@link #extraExcludes}（用户配置追加）。
 * <p>
 * 配置示例（application.properties）：
 * <pre>
 * # 追加放行路径（在默认排除之上额外放行）
 * geelato.security.interceptor.extra-excludes[0]=/mql-playground.html
 * geelato.security.interceptor.extra-excludes[1]=/mql-playground
 * geelato.security.interceptor.extra-excludes[2]=/api/mql/**
 *
 * # 完全覆盖默认排除（谨慎使用，会丢弃内置默认）
 * geelato.security.interceptor.override-default-excludes=true
 * geelato.security.interceptor.default-excludes[0]=/api/oauth2/login
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "geelato.security.interceptor")
public class SecurityInterceptorProperties {

    /**
     * 内置默认排除路径（向后兼容，保留原有硬编码的全部路径）。
     * <p>
     * 默认值在 {@link #initDefaultExcludes()} 中初始化。用户可通过
     * {@code geelato.security.interceptor.default-excludes[*]} 覆盖，但通常无需修改。
     */
    private List<String> defaultExcludes = new ArrayList<>();

    /**
     * 用户配置追加的排除路径（在默认排除之上额外放行）。
     * <p>
     * 配置项：{@code geelato.security.interceptor.extra-excludes[*]}
     */
    private List<String> extraExcludes = new ArrayList<>();

    /**
     * 是否覆盖默认排除（true 时仅使用 default-excludes 配置值，不再合并内置默认）。
     * 默认 false（合并内置默认 + 用户配置）。
     */
    private boolean overrideDefaultExcludes = false;

    /**
     * 返回最终生效的全部排除路径。
     * <ul>
     *   <li>{@code overrideDefaultExcludes=false}：内置默认 + defaultExcludes 配置 + extraExcludes</li>
     *   <li>{@code overrideDefaultExcludes=true}：仅 defaultExcludes 配置 + extraExcludes</li>
     * </ul>
     */
    public List<String> resolveEffectiveExcludes() {
        List<String> effective = new ArrayList<>();
        if (!overrideDefaultExcludes) {
            effective.addAll(initDefaultExcludes());
        }
        if (defaultExcludes != null) {
            effective.addAll(defaultExcludes);
        }
        if (extraExcludes != null) {
            effective.addAll(extraExcludes);
        }
        return effective;
    }

    /**
     * 内置默认排除路径（与重构前 InterceptorConfiguration 硬编码的路径一致，保证向后兼容）。
     */
    private List<String> initDefaultExcludes() {
        String api = "/api";
        return new ArrayList<>(Arrays.asList(
                // 登录接口
                api + "/oauth2/login",
                api + "/oauth2/refreshToken",
                api + "/auth/login",
                // 静态资源
                "/assets/**",
                // 错误页面
                "/error/**",
                // swagger-ui 相关
                "/v3/**",
                "/swagger-ui/**",
                "/swagger-ui/index.html",
                // starter 就绪检查
                api + "/scaffold/ready",
                // 重置密码接口
                api + "/user/forgetValid",
                api + "/user/forget",
                api + "/code/generate/**",
                // 未登录前相关配置文件
                api + "/resources/json",
                // 加载或下载文件
                api + "/resources/file",
                // 分支识别
                api + "/branch",
                // 微信回调接口
                "/wx/callback/hook",
                // 微信登录接口
                "/wx/login/**",
                // 微信重定向接口
                "/wx/redirect",
                // oauth2登录接口
                "/oauth2/**",
                // 监控页面
                "/monitor/**",
                "/wx/validate/**",
                // MQL Playground（在线调试与测试场景集，由 geelato.mql.playground.enabled 控制是否激活）
                "/mql-playground.html",
                "/mql-playground",
                "/api/mql/**"
        ));
    }
}
