package cn.geelato.web.platform.boot;


import cn.geelato.web.platform.shiro.DbRealm;
import cn.geelato.web.platform.shiro.OAuth2Realm;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.cache.ehcache.EhCacheManager;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.spring.LifecycleBeanPostProcessor;
import org.apache.shiro.spring.security.interceptor.AuthorizationAttributeSourceAdvisor;
import org.apache.shiro.spring.web.ShiroFilterFactoryBean;
import org.apache.shiro.util.ThreadContext;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author  hongxq
 */
@Configuration
@Slf4j

public class ShiroConfiguration extends BaseConfiguration {


    @Bean
    public ShiroFilterFactoryBean getShiroFilterFactoryBean(
            @Qualifier("oauth2SecurityManager")DefaultWebSecurityManager securityManager) {
        ShiroFilterFactoryBean shiroFilterFactoryBean = new ShiroFilterFactoryBean();
        shiroFilterFactoryBean.setSecurityManager(securityManager);
        Map<String, String> filterChainDefinitionMap = new LinkedHashMap<>();
        shiroFilterFactoryBean.setFilterChainDefinitionMap(filterChainDefinitionMap);
        return shiroFilterFactoryBean;
    }

    @Bean
    public AuthorizationAttributeSourceAdvisor getAuthorizationAttributeSourceAdvisor(
            @Qualifier("oauth2SecurityManager")DefaultWebSecurityManager securityManager) {
        AuthorizationAttributeSourceAdvisor authorizationAttributeSourceAdvisor = new AuthorizationAttributeSourceAdvisor();
        authorizationAttributeSourceAdvisor.setSecurityManager(securityManager);
        return authorizationAttributeSourceAdvisor;
    }
    @Bean
    public EhCacheManager getEhCacheManager() {
        EhCacheManager em = new EhCacheManager();
        em.setCacheManagerConfigFile("classpath:ehcache/ehcache-shiro.xml");
        return em;
    }

    @Bean(name = "dbRealm")
    @ConditionalOnProperty(value = "geelato.application.shiro",havingValue = "db")
    public DbRealm dbRealm(EhCacheManager cacheManager) {
        DbRealm realm = new DbRealm();
        realm.setCacheManager(cacheManager);
        return realm;
    }
    @Bean(name = "oauth2Realm")
    @ConditionalOnProperty(value = "geelato.application.shiro",havingValue = "oauth2")
    public OAuth2Realm shiroRealm(EhCacheManager cacheManager) {
        OAuth2Realm realm = new OAuth2Realm();
        realm.setCacheManager(cacheManager);
        return realm;
    }
    @Bean(name = "dbSecurityManager")
    @ConditionalOnProperty(value = "geelato.application.shiro",havingValue = "db")
    public DefaultWebSecurityManager dbSecurityManager(DbRealm dbRealm) {
        DefaultWebSecurityManager defaultWebSecurityManager = new DefaultWebSecurityManager();
        defaultWebSecurityManager.setRealm(dbRealm);
        defaultWebSecurityManager.setCacheManager(getEhCacheManager());
        ThreadContext.bind(defaultWebSecurityManager);
        return defaultWebSecurityManager;
    }

    @Bean(name = "oauth2SecurityManager")
    @ConditionalOnProperty(value = "geelato.application.shiro",havingValue = "oauth2")
    public DefaultWebSecurityManager oauth2SecurityManager(OAuth2Realm oauth2Realm) {
        DefaultWebSecurityManager defaultWebSecurityManager = new DefaultWebSecurityManager();
        defaultWebSecurityManager.setRealm(oauth2Realm);
        defaultWebSecurityManager.setCacheManager(getEhCacheManager());
        ThreadContext.bind(defaultWebSecurityManager);
        return defaultWebSecurityManager;
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








}