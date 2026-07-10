package cn.geelato.orm.runtime;

import cn.geelato.core.orm.Dao;
import cn.geelato.orm.config.OrmProperties;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.StringJoiner;

/**
 * ORM 运行时 Dao 解析器。
 * 统一封装 MetaCommandExecutor 所需的 Dao 选择规则，避免配置类和 DSL 入口各自维护一套分支逻辑。
 */
public final class OrmDaoResolver {

    private OrmDaoResolver() {
    }

    public static Dao resolve(ApplicationContext applicationContext, OrmProperties ormProperties) {
        if (ormProperties != null && StringUtils.hasText(ormProperties.getDaoBeanName())) {
            return resolveConfiguredDao(applicationContext, ormProperties.getDaoBeanName());
        }

        try {
            return applicationContext.getBean(Dao.class);
        } catch (NoUniqueBeanDefinitionException ex) {
            return resolveCompatibilityDao(applicationContext, ex);
        } catch (NoSuchBeanDefinitionException ex) {
            throw new IllegalStateException("No Dao bean found for MetaCommandExecutor", ex);
        }
    }

    private static Dao resolveConfiguredDao(ApplicationContext applicationContext, String daoBeanName) {
        if (!applicationContext.containsBean(daoBeanName)) {
            throw new IllegalStateException("Configured Dao bean not found: " + daoBeanName);
        }
        return applicationContext.getBean(daoBeanName, Dao.class);
    }

    private static Dao resolveCompatibilityDao(ApplicationContext applicationContext, NoUniqueBeanDefinitionException ex) {
        Map<String, Dao> daoBeans = applicationContext.getBeansOfType(Dao.class);
        if (daoBeans.size() == 1) {
            return daoBeans.values().iterator().next();
        }
        if (daoBeans.containsKey("dynamicDao")) {
            return daoBeans.get("dynamicDao");
        }
        if (daoBeans.containsKey("primaryDao")) {
            return daoBeans.get("primaryDao");
        }
        throw new IllegalStateException(buildAmbiguousDaoMessage(daoBeans), ex);
    }

    private static String buildAmbiguousDaoMessage(Map<String, Dao> daoBeans) {
        StringJoiner joiner = new StringJoiner(", ");
        for (String beanName : daoBeans.keySet()) {
            joiner.add(beanName);
        }
        return "Multiple Dao beans found for MetaCommandExecutor: [" + joiner + "], please configure geelato.orm.dao-bean-name";
    }
}
