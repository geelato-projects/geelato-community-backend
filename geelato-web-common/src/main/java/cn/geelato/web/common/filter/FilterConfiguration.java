package cn.geelato.web.common.filter;

import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SuppressWarnings("rawtypes")
public class FilterConfiguration {
    @Bean
    public FilterRegistrationBean filterRegistration() {
        FilterRegistrationBean registration = new FilterRegistrationBean();
        registration.setFilter(cacheHttpServletFilter());
        registration.addUrlPatterns("/*");
        registration.setName("cacheHttpServletFilter");
        return registration;
    }

    @Bean(name = "cacheHttpServletFilter")
    public Filter cacheHttpServletFilter() {
        return new CacheHttpServletFilter();
    }
}
