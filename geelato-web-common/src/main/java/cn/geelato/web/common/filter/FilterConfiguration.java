package cn.geelato.web.common.filter;

import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SuppressWarnings("rawtypes")
public class FilterConfiguration {
    @Bean
    public FilterRegistrationBean cacheFilterRegistration() {
        FilterRegistrationBean<CacheHttpServletFilter> cacheHttpServletFilterFilterRegistrationBean = new FilterRegistrationBean<>();
        cacheHttpServletFilterFilterRegistrationBean.setFilter(cacheHttpServletFilter());
        cacheHttpServletFilterFilterRegistrationBean.addUrlPatterns("/*");
        cacheHttpServletFilterFilterRegistrationBean.setName("cacheHttpServletFilter");
        cacheHttpServletFilterFilterRegistrationBean.setOrder(2);
        return cacheHttpServletFilterFilterRegistrationBean;
    }

    @Bean(name = "cacheHttpServletFilter")
    public CacheHttpServletFilter cacheHttpServletFilter() {
        return new CacheHttpServletFilter();
    }

    @Bean
    @ConditionalOnExpression("#{T(cn.geelato.core.GlobalContext).getApiEncryptOption()}")
    public FilterRegistrationBean decryptFilterRegistration() {
        FilterRegistrationBean<DecryptHttpServletFilter> decryptHttpServletFilterFilterRegistrationBean = new FilterRegistrationBean<>();
        decryptHttpServletFilterFilterRegistrationBean.setFilter(decryptHttpServletFilter());
        decryptHttpServletFilterFilterRegistrationBean.addUrlPatterns("/*");
        decryptHttpServletFilterFilterRegistrationBean.setName("decryptHttpServletFilter");
        decryptHttpServletFilterFilterRegistrationBean.setOrder(0);
        return decryptHttpServletFilterFilterRegistrationBean;
    }
    @Bean(name = "decryptHttpServletFilter")
    @ConditionalOnExpression("#{T(cn.geelato.core.GlobalContext).getApiEncryptOption()}")
    public DecryptHttpServletFilter decryptHttpServletFilter() {
        return new DecryptHttpServletFilter();
    }

    @Bean
    public FilterRegistrationBean securityContextFilterRegistration() {
        FilterRegistrationBean<SecurityContextFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(securityContextFilter());
        registrationBean.addUrlPatterns("/*");
        registrationBean.setName("securityContextFilter");
        registrationBean.setOrder(1);
        return registrationBean;
    }

    @Bean(name = "securityContextFilter")
    public SecurityContextFilter securityContextFilter() {
        return new SecurityContextFilter();
    }

    @Bean
    public FilterRegistrationBean experimentFilterRegistration() {
        FilterRegistrationBean<ExperimentFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(experimentFilter());
        registrationBean.addUrlPatterns("/*");
        registrationBean.setName("experimentFilter");
        // 在安全上下文(1)/缓存(2)/审计(3)之后、Controller 之前，解析 X-Gl-Experiments 请求头
        registrationBean.setOrder(4);
        return registrationBean;
    }

    @Bean(name = "experimentFilter")
    public ExperimentFilter experimentFilter() {
        return new ExperimentFilter();
    }
}
