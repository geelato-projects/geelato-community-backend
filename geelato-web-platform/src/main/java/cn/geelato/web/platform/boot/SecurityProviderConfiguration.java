package cn.geelato.web.platform.boot;

import cn.geelato.security.DefaultOrgProvider;
import cn.geelato.security.DefaultUserOrgInfoEnricher;
import cn.geelato.security.DefaultUserProvider;
import cn.geelato.security.OrgProvider;
import cn.geelato.security.SecurityDataRefreshCoordinator;
import cn.geelato.security.UserOrgInfoEnricher;
import cn.geelato.security.UserProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class SecurityProviderConfiguration {

    @Bean
    @ConditionalOnMissingBean(OrgProvider.class)
    public OrgProvider orgProvider(@Qualifier("primaryJdbcTemplate") JdbcTemplate jdbcTemplate) {
        return new DefaultOrgProvider(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(UserOrgInfoEnricher.class)
    public UserOrgInfoEnricher userOrgInfoEnricher(OrgProvider orgProvider) {
        return new DefaultUserOrgInfoEnricher(orgProvider);
    }

    @Bean
    @ConditionalOnMissingBean(UserProvider.class)
    public UserProvider userProvider(@Qualifier("primaryJdbcTemplate") JdbcTemplate jdbcTemplate,
                                     UserOrgInfoEnricher userOrgInfoEnricher) {
        return new DefaultUserProvider(jdbcTemplate, userOrgInfoEnricher);
    }

    @Bean
    @ConditionalOnMissingBean(SecurityDataRefreshCoordinator.class)
    public SecurityDataRefreshCoordinator securityDataRefreshCoordinator(OrgProvider orgProvider,
                                                                         UserProvider userProvider) {
        return new SecurityDataRefreshCoordinator(orgProvider, userProvider);
    }
}
