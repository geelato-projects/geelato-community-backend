package cn.geelato.orm.runtime;

import cn.geelato.orm.config.OrmProperties;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.util.Map;
import java.util.StringJoiner;

/**
 * ORM 运行时 JdbcTemplate 解析器。
 */
public final class OrmJdbcTemplateResolver {

    private OrmJdbcTemplateResolver() {
    }

    public static JdbcTemplate resolve(ApplicationContext applicationContext, OrmProperties ormProperties) {
        if (ormProperties != null && StringUtils.hasText(ormProperties.getJdbcTemplateBeanName())) {
            return resolveConfiguredJdbcTemplate(applicationContext, ormProperties.getJdbcTemplateBeanName());
        }

        String defaultDataSourceKey = ormProperties != null ? ormProperties.getDefaultDataSourceKey() : null;
        if (StringUtils.hasText(defaultDataSourceKey)) {
            JdbcTemplate keyedJdbcTemplate = resolveByDefaultKey(applicationContext, defaultDataSourceKey);
            if (keyedJdbcTemplate != null) {
                return keyedJdbcTemplate;
            }
        }

        try {
            return applicationContext.getBean(JdbcTemplate.class);
        } catch (NoUniqueBeanDefinitionException ex) {
            throw new IllegalStateException(buildAmbiguousJdbcTemplateMessage(applicationContext.getBeansOfType(JdbcTemplate.class)), ex);
        } catch (Exception ignored) {
            Map<String, DataSource> dataSourceBeans = applicationContext.getBeansOfType(DataSource.class);
            if (dataSourceBeans.size() == 1) {
                return new JdbcTemplate(dataSourceBeans.values().iterator().next());
            }
            throw new IllegalStateException(buildMissingJdbcTemplateMessage(dataSourceBeans));
        }
    }

    private static JdbcTemplate resolveConfiguredJdbcTemplate(ApplicationContext applicationContext, String beanName) {
        if (!applicationContext.containsBean(beanName)) {
            throw new IllegalStateException("Configured JdbcTemplate bean not found: " + beanName);
        }
        return applicationContext.getBean(beanName, JdbcTemplate.class);
    }

    private static JdbcTemplate resolveByDefaultKey(ApplicationContext applicationContext, String defaultDataSourceKey) {
        if (applicationContext.containsBean(defaultDataSourceKey)) {
            Object bean = applicationContext.getBean(defaultDataSourceKey);
            if (bean instanceof JdbcTemplate jdbcTemplate) {
                return jdbcTemplate;
            }
            if (bean instanceof DataSource dataSource) {
                return new JdbcTemplate(dataSource);
            }
        }
        String jdbcTemplateBeanName = defaultDataSourceKey + "JdbcTemplate";
        if (applicationContext.containsBean(jdbcTemplateBeanName)) {
            return applicationContext.getBean(jdbcTemplateBeanName, JdbcTemplate.class);
        }
        String dataSourceBeanName = defaultDataSourceKey + "DataSource";
        if (applicationContext.containsBean(dataSourceBeanName)) {
            return new JdbcTemplate(applicationContext.getBean(dataSourceBeanName, DataSource.class));
        }
        return null;
    }

    private static String buildAmbiguousJdbcTemplateMessage(Map<String, JdbcTemplate> jdbcTemplates) {
        StringJoiner joiner = new StringJoiner(", ");
        jdbcTemplates.keySet().forEach(joiner::add);
        return "Multiple JdbcTemplate beans found for MetaCommandExecutor: [" + joiner + "], please configure geelato.orm.jdbc-template-bean-name";
    }

    private static String buildMissingJdbcTemplateMessage(Map<String, DataSource> dataSourceBeans) {
        if (dataSourceBeans.isEmpty()) {
            return "No JdbcTemplate or DataSource bean found for MetaCommandExecutor";
        }
        if (dataSourceBeans.size() > 1) {
            StringJoiner joiner = new StringJoiner(", ");
            dataSourceBeans.keySet().forEach(joiner::add);
            return "Multiple DataSource beans found for MetaCommandExecutor: [" + joiner + "], please configure geelato.orm.jdbc-template-bean-name";
        }
        return "No JdbcTemplate bean found for MetaCommandExecutor";
    }
}
