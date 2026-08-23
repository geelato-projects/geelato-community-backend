package cn.geelato.web.platform.boot;


import cn.geelato.core.orm.Dao;
import cn.geelato.web.common.shiro.*;
import jakarta.servlet.DispatcherType;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.cache.ehcache.EhCacheManager;
import org.apache.shiro.spring.LifecycleBeanPostProcessor;
import org.apache.shiro.spring.security.interceptor.AuthorizationAttributeSourceAdvisor;
import org.apache.shiro.spring.web.ShiroFilterFactoryBean;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.apache.shiro.web.servlet.AbstractShiroFilter;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import java.util.*;

@Configuration
@Slf4j
public class ShiroConfiguration extends BaseConfiguration {

    @Bean
    public ShiroFilterFactoryBean getShiroFilterFactoryBean(DefaultWebSecurityManager securityManager) {
        ShiroFilterFactoryBean shiroFilterFactoryBean = new ShiroFilterFactoryBean();
        shiroFilterFactoryBean.setSecurityManager(securityManager);
        Map<String, String> filterChainDefinitionMap = new LinkedHashMap<>();
        filterChainDefinitionMap.put("/**", "anon");
        shiroFilterFactoryBean.setFilterChainDefinitionMap(filterChainDefinitionMap);
        return shiroFilterFactoryBean;
    }

    /**
     * 显式注册 shiroFilter 并加入 ASYNC 分发类型。
     * Boot 自动注册的 Filter 默认只拦截 REQUEST 分发；SseEmitter（/api/ai/ask、/subscribe/**）
     * 结束后的 ASYNC 二次分发不经过 Shiro 过滤器，线程上无 SecurityManager，
     * FrameworkServlet 发布请求处理事件时经 ShiroHttpServletRequest.getUserPrincipal()
     * 抛 UnavailableSecurityManagerException。此注册使 ASYNC 分发同样绑定 Subject，消除该异常。
     */
    @Bean
    public FilterRegistrationBean<AbstractShiroFilter> shiroFilterRegistration(AbstractShiroFilter shiroFilter) {
        FilterRegistrationBean<AbstractShiroFilter> registration = new FilterRegistrationBean<>(shiroFilter);
        registration.addUrlPatterns("/*");
        registration.setName("shiroFilter");
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC);
        registration.setOrder(0);
        return registration;
    }

    @Bean
    public AuthorizationAttributeSourceAdvisor getAuthorizationAttributeSourceAdvisor(
            DefaultWebSecurityManager securityManager) {
        AuthorizationAttributeSourceAdvisor authorizationAttributeSourceAdvisor = new AuthorizationAttributeSourceAdvisor();
        authorizationAttributeSourceAdvisor.setSecurityManager(securityManager);
        return authorizationAttributeSourceAdvisor;
    }

    @Bean
    public EhCacheManager getEhCacheManager() {
        EhCacheManager em = new EhCacheManager();
        em.setCacheManagerConfigFile("classpath:shiro/ehcache.xml");
        return em;
    }

    @Bean(name = "lifecycleBeanPostProcessor")
    public LifecycleBeanPostProcessor getLifecycleBeanPostProcessor() {
        return new LifecycleBeanPostProcessor();
    }

    @Bean
    @DependsOn("lifecycleBeanPostProcessor")
    public DefaultAdvisorAutoProxyCreator getDefaultAdvisorAutoProxyCreator() {
        DefaultAdvisorAutoProxyCreator defaultAdvisorAutoProxyCreator = new DefaultAdvisorAutoProxyCreator();
        defaultAdvisorAutoProxyCreator.setUsePrefix(false);
        defaultAdvisorAutoProxyCreator.setProxyTargetClass(true);
        return defaultAdvisorAutoProxyCreator;
    }


    @Bean(name = "dbShiroRealm")
    public DbRealm dbRealm(@Qualifier("primaryDao") Dao dao, EhCacheManager cacheManager) {
        DbRealm realm = new DbRealm(dao);
        realm.setCacheManager(cacheManager);
        return realm;
    }

    @Bean(name = "anonymousRealm")
    public AnonymousRealm anonymousRealm(@Qualifier("primaryDao") Dao dao, EhCacheManager cacheManager) {
        AnonymousRealm realm = new AnonymousRealm(dao);
        realm.setCacheManager(cacheManager);
        return realm;
    }

    @Bean(name = "oauth2Realm")
    public OAuth2Realm oauth2Realm(EhCacheManager cacheManager) {
        OAuth2Realm realm = new OAuth2Realm();
        realm.setCacheManager(cacheManager);
        return realm;
    }

    @Bean(name = "weixinUnionIdRealm")
    public WeixinUnionIdRealm weixinUnionIdRealm(@Qualifier("primaryDao") Dao dao, EhCacheManager cacheManager) {
        WeixinUnionIdRealm realm = new WeixinUnionIdRealm(dao);
        realm.setCacheManager(cacheManager);
        return realm;
    }

    @Bean(name = "weixinWorkUserIdRealm")
    public WeixinWorkUserIdRealm weixinWorkUserIdRealm(@Qualifier("primaryDao") Dao dao, EhCacheManager cacheManager) {
        WeixinWorkUserIdRealm realm = new WeixinWorkUserIdRealm(dao);
        realm.setCacheManager(cacheManager);
        return realm;
    }

    @Bean(name = "systemTokenRealm")
    public SystemTokenRealm systemTokenRealm(cn.geelato.web.common.interceptor.SystemTokenProperties systemTokenProperties,
                                             EhCacheManager cacheManager) {
        SystemTokenRealm realm = new SystemTokenRealm(systemTokenProperties);
        realm.setCacheManager(cacheManager);
        return realm;
    }

    @Bean(name = "defaultSecurityManager")
    public DefaultWebSecurityManager defaultSecurityManager(
            @Qualifier("anonymousRealm") AnonymousRealm anonymousRealm,
            @Qualifier("oauth2Realm") OAuth2Realm oAuth2Realm,
            @Qualifier("weixinUnionIdRealm") WeixinUnionIdRealm weixinUnionIdRealm,
            @Qualifier("weixinWorkUserIdRealm") WeixinWorkUserIdRealm weixinWorkUserIdRealm,
            @Qualifier("systemTokenRealm") SystemTokenRealm systemTokenRealm,
            @Qualifier("dbShiroRealm") DbRealm dbRealm,
            EhCacheManager cacheManager) {
        DefaultWebSecurityManager defaultWebSecurityManager = new DefaultWebSecurityManager();
        defaultWebSecurityManager.setRealms(Arrays.asList(anonymousRealm, weixinUnionIdRealm, weixinWorkUserIdRealm, oAuth2Realm, systemTokenRealm, dbRealm));
        defaultWebSecurityManager.setCacheManager(cacheManager);
        return defaultWebSecurityManager;
    }
}
