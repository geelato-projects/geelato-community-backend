package cn.geelato.orm.runtime;

import cn.geelato.core.orm.Dao;
import cn.geelato.orm.config.OrmProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.StringJoiner;

/**
 * ORM 运行时 Dao 解析器。
 * 统一封装 MetaCommandExecutor 所需的 Dao 选择规则，避免配置类和 DSL 入口各自维护一套分支逻辑。
 *
 * <p>自动解析优先级：dynamicDao &gt; primaryDao &gt; 唯一的 Dao Bean。
 * dynamicDao 基于 DynamicRoutingDataSource，是唯一能消费 useDataSource(connectId) /
 * 实体 connectId 路由 key 的 Dao；绑定 primaryDao 时切库不生效，因此只要容器中存在
 * dynamicDao，ORM 必须绑定它，primaryDao 仅作为无动态数据源体系时的回退。
 */
public final class OrmDaoResolver {

    public static final String DYNAMIC_DAO_BEAN_NAME = "dynamicDao";
    public static final String PRIMARY_DAO_BEAN_NAME = "primaryDao";

    private OrmDaoResolver() {
    }

    public static Dao resolve(ApplicationContext applicationContext, OrmProperties ormProperties) {
        if (ormProperties != null && StringUtils.hasText(ormProperties.getDaoBeanName())) {
            return resolveConfiguredDao(applicationContext, ormProperties.getDaoBeanName());
        }
        if (applicationContext.containsBean(DYNAMIC_DAO_BEAN_NAME)) {
            return applicationContext.getBean(DYNAMIC_DAO_BEAN_NAME, Dao.class);
        }
        if (applicationContext.containsBean(PRIMARY_DAO_BEAN_NAME)) {
            return applicationContext.getBean(PRIMARY_DAO_BEAN_NAME, Dao.class);
        }
        return resolveSingleDao(applicationContext);
    }

    private static Dao resolveConfiguredDao(ApplicationContext applicationContext, String daoBeanName) {
        if (!applicationContext.containsBean(daoBeanName)) {
            throw new IllegalStateException("Configured Dao bean not found: " + daoBeanName);
        }
        return applicationContext.getBean(daoBeanName, Dao.class);
    }

    private static Dao resolveSingleDao(ApplicationContext applicationContext) {
        Map<String, Dao> daoBeans = applicationContext.getBeansOfType(Dao.class);
        if (daoBeans.size() == 1) {
            return daoBeans.values().iterator().next();
        }
        throw new IllegalStateException(buildNoUsableDaoMessage(daoBeans));
    }

    private static String buildNoUsableDaoMessage(Map<String, Dao> daoBeans) {
        if (daoBeans.isEmpty()) {
            return "No Dao bean found for MetaCommandExecutor";
        }
        StringJoiner joiner = new StringJoiner(", ");
        for (String beanName : daoBeans.keySet()) {
            joiner.add(beanName);
        }
        return "Multiple Dao beans found for MetaCommandExecutor: [" + joiner
                + "], and none of them is named 'dynamicDao' or 'primaryDao'."
                + " Please configure geelato.orm.dao-bean-name";
    }
}
