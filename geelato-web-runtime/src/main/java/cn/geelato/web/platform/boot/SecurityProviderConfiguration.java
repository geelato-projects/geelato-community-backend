package cn.geelato.web.platform.boot;

import cn.geelato.security.DefaultOrgProvider;
import cn.geelato.security.DefaultUserOrgInfoEnricher;
import cn.geelato.security.DefaultUserProvider;
import cn.geelato.security.JdbcOrgSnapshotLoader;
import cn.geelato.security.JdbcUserSnapshotLoader;
import cn.geelato.security.OrgProvider;
import cn.geelato.security.OrgSnapshotLoader;
import cn.geelato.security.SecurityDataRefreshCoordinator;
import cn.geelato.security.UserOrgInfoEnricher;
import cn.geelato.security.UserProvider;
import cn.geelato.security.UserSnapshotLoader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class SecurityProviderConfiguration {

    @Bean
    @ConditionalOnMissingBean(OrgSnapshotLoader.class)
    public OrgSnapshotLoader orgSnapshotLoader(@Qualifier("primaryJdbcTemplate") JdbcTemplate jdbcTemplate) {
        return new JdbcOrgSnapshotLoader(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(OrgProvider.class)
    public OrgProvider orgProvider(OrgSnapshotLoader orgSnapshotLoader) {
        return new DefaultOrgProvider(orgSnapshotLoader);
    }

    @Bean
    @ConditionalOnMissingBean(UserOrgInfoEnricher.class)
    public UserOrgInfoEnricher userOrgInfoEnricher(OrgProvider orgProvider) {
        return new DefaultUserOrgInfoEnricher(orgProvider);
    }

    @Bean
    @ConditionalOnMissingBean(UserSnapshotLoader.class)
    public UserSnapshotLoader userSnapshotLoader(@Qualifier("primaryJdbcTemplate") JdbcTemplate jdbcTemplate) {
        return new JdbcUserSnapshotLoader(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(UserProvider.class)
    public UserProvider userProvider(UserSnapshotLoader userSnapshotLoader,
                                     UserOrgInfoEnricher userOrgInfoEnricher) {
        return new DefaultUserProvider(userSnapshotLoader, userOrgInfoEnricher);
    }

    @Bean
    @ConditionalOnMissingBean(SecurityDataRefreshCoordinator.class)
    public SecurityDataRefreshCoordinator securityDataRefreshCoordinator(OrgProvider orgProvider,
                                                                         UserProvider userProvider) {
        return new SecurityDataRefreshCoordinator(orgProvider, userProvider);
    }
}
