package cn.geelato.web.platform.filter;

import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

//@Configuration
public class FilterConfiguration {
    @Bean
    public FilterRegistrationBean filterRegistration() {
        FilterRegistrationBean registration = new FilterRegistrationBean();
        registration.setFilter(httpServletFilter());
        registration.addUrlPatterns("/*");
        registration.setName("httpServletFilter");
        return registration;
    }

    @Bean(name = "httpServletFilter")
    public Filter httpServletFilter() {
        return new HttpServletFilter();
    }
}
