package cn.geelato.web.platform.boot;


import cn.geelato.core.orm.Dao;
import cn.geelato.web.common.shiro.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.cache.ehcache.EhCacheManager;
import org.apache.shiro.spring.LifecycleBeanPostProcessor;
import org.apache.shiro.spring.security.interceptor.AuthorizationAttributeSourceAdvisor;
import org.apache.shiro.spring.web.ShiroFilterFactoryBean;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.beans.factory.annotation.Qualifier;
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

    @Bean(name = "defaultSecurityManager")
    public DefaultWebSecurityManager defaultSecurityManager(
            @Qualifier("anonymousRealm") AnonymousRealm anonymousRealm,
            @Qualifier("oauth2Realm") OAuth2Realm oAuth2Realm,
            @Qualifier("weixinUnionIdRealm") WeixinUnionIdRealm weixinUnionIdRealm,
            @Qualifier("weixinWorkUserIdRealm") WeixinWorkUserIdRealm weixinWorkUserIdRealm,
            @Qualifier("dbShiroRealm") DbRealm dbRealm,
            EhCacheManager cacheManager) {
        DefaultWebSecurityManager defaultWebSecurityManager = new DefaultWebSecurityManager();
        defaultWebSecurityManager.setRealms(Arrays.asList(anonymousRealm, weixinUnionIdRealm, weixinWorkUserIdRealm, oAuth2Realm, dbRealm));
        defaultWebSecurityManager.setCacheManager(cacheManager);
        return defaultWebSecurityManager;
    }
}
