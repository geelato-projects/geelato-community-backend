package cn.geelato.web.platform.boot;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * @author geelato
 */
@Configuration
public class CorsConfiguration extends BaseConfiguration {

    private org.springframework.web.cors.CorsConfiguration buildConfig() {
        org.springframework.web.cors.CorsConfiguration corsConfiguration = new org.springframework.web.cors.CorsConfiguration();
        // 1 允许任何域名使用
        corsConfiguration.addAllowedOriginPattern("*");
        // 2 允许任何头
        corsConfiguration.addAllowedHeader("*");
        // 3 允许任何方法（post、get等）
        corsConfiguration.addAllowedMethod("*");
        // 4 CORS请求默认不发送Cookie和HTTP认证信息，若需把Cookie发到服务器，Access-Control-Allow-Credentials: true
        // spring boot 2.4.X配置有变化，allow-credentials: true 和 allowed-origins: “*” 不能共存，及当allow-credentials配置为true时，allowed-origins不能配置为 *
        corsConfiguration.setAllowCredentials(true);
        // 5 每次预检请求的通过的有效期为7200秒,即2小时
        corsConfiguration.setMaxAge(7200L);
        return corsConfiguration;
    }

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", buildConfig()); // 4
        return new CorsFilter(source);
    }
}