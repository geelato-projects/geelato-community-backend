package cn.geelato.datasource.interceptor;

import cn.geelato.core.ds.DataSourceManager;
import cn.geelato.core.mql.execute.BoundPageSql;
import cn.geelato.core.mql.execute.BoundSql;
import cn.geelato.datasource.DynamicDataSourceHolder;
import cn.geelato.datasource.EntityDataSourceResolver;
import cn.geelato.datasource.annotation.UseDynamicDataSource;
import cn.geelato.lang.meta.Entity;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 动态数据源拦截器
 * 负责在方法执行前后进行数据源切换
 */
@Aspect
@Component
@Slf4j
public class DataSourceInterceptor {
    @Autowired
    private EntityDataSourceResolver entityDataSourceResolver;
    
    /**
     * 默认数据源线程本地变量
     */
    private static final ThreadLocal<String> DEFAULT_DATA_SOURCE = new ThreadLocal<>();

    /**
     * 所有兜底均失效时的最终数据源 key
     */
    private static final String PRIMARY_DATASOURCE_KEY = "primary";

    /**
     * 事务开始前的数据源设置
     * 拦截被@UseDynamicDataSource注解标注的类或方法。
     * 注意：字段级@UseDynamicDataSource仅作为dynamicDao注入标记（见DynamicDaoFieldProcessor），
     * 不会命中本切面；默认数据源的兜底逻辑见{@link #resolveDataSourceKey(String)}。
     */
    @Before("@within(cn.geelato.datasource.annotation.UseDynamicDataSource) " +
            "|| @annotation(cn.geelato.datasource.annotation.UseDynamicDataSource)")
    public void beforeTransaction(JoinPoint point) {
        UseDynamicDataSource dataSourceAnnotation = resolveUseDynamicDataSource(point);
        if (dataSourceAnnotation != null) {
            String defaultDataSource = dataSourceAnnotation.value();
            DEFAULT_DATA_SOURCE.set(defaultDataSource);
            log.debug("设置默认数据源: {}", defaultDataSource);
        }
    }

    /**
     * 方法结束后清理默认数据源上下文，避免线程复用时沿用上一次的默认值。
     */
    @After("@within(cn.geelato.datasource.annotation.UseDynamicDataSource) " +
            "|| @annotation(cn.geelato.datasource.annotation.UseDynamicDataSource)")
    public void afterUseDynamicDataSource() {
        DEFAULT_DATA_SOURCE.remove();
        log.debug("清理默认数据源上下文");
    }

    @Around("execution(* cn.geelato.core.orm.Dao.*(..))")
    public Object aroundDaoMethod(ProceedingJoinPoint pjp) throws Throwable {
        String entityName = resolveEntityName(pjp.getArgs());
        String dataSourceKey = resolveDataSourceKey(entityName);
        String previous = DynamicDataSourceHolder.getDataSourceKey();
        try {
            applyDataSourceKey(dataSourceKey);
            return pjp.proceed();
        } finally {
            restorePreviousDataSource(previous);
        }
    }

    private UseDynamicDataSource resolveUseDynamicDataSource(JoinPoint point) {
        Method method = ((MethodSignature) point.getSignature()).getMethod();
        UseDynamicDataSource annotation = method.getAnnotation(UseDynamicDataSource.class);
        if (annotation != null) {
            return annotation;
        }
        return AnnotationUtils.findAnnotation(method.getDeclaringClass(), UseDynamicDataSource.class);
    }

    /**
     * 从方法参数中解析实体名称。
     * <p>按参数顺序识别：BoundPageSql/BoundSql（command 携带 entityName）、
     * 带 {@link Entity} 注解的 Class、非空 List（batchSave/multiSave/multiDelete 等，
     * 取首元素按同样规则识别）、带 {@link Entity} 注解的实体实例（insert/save/update）。
     * 无法识别的参数跳过并继续检查后续参数，全部无法识别时返回 null 走默认数据源兜底链。
     */
    String resolveEntityName(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof BoundPageSql bps) {
                return bps.getBoundSql() != null && bps.getBoundSql().getCommand() != null
                        ? bps.getBoundSql().getCommand().getEntityName()
                        : null;
            }
            if (arg instanceof BoundSql bs) {
                return bs.getCommand() != null ? bs.getCommand().getEntityName() : null;
            }
            if (arg instanceof Class<?> clazz && clazz.isAnnotationPresent(Entity.class)) {
                return resolveEntityNameFromClass(clazz);
            }
            if (arg instanceof List<?> list && !list.isEmpty()) {
                String entityName = resolveEntityName(new Object[]{list.get(0)});
                if (entityName != null) {
                    return entityName;
                }
                continue;
            }
            if (arg != null && arg.getClass().isAnnotationPresent(Entity.class)) {
                return resolveEntityNameFromClass(arg.getClass());
            }
        }
        return null;
    }

    private String resolveEntityNameFromClass(Class<?> clazz) {
        Entity entityAnnotation = clazz.getAnnotation(Entity.class);
        return entityAnnotation.name().isEmpty() ? clazz.getSimpleName() : entityAnnotation.name();
    }

    /**
     * 解析数据源 key，保证返回非 null 的具体 key。
     * <p>解析优先级：类/方法级注解值 &gt; 实体映射 &gt; 外层已显式设置的 key &gt; 平台默认 key &gt; primary。
     * <p>字段级@UseDynamicDataSource仅为注入标记，不产生注解作用域默认值，
     * 因此{@link #DEFAULT_DATA_SOURCE}在纯字段注解场景下为空，由后续兜底链接管。
     */
    String resolveDataSourceKey(String entityName) {
        if (entityName == null) {
            return resolveDefaultDataSourceKey();
        }

        String dataSourceKey = entityDataSourceResolver.resolveDataSource(entityName);
        if (dataSourceKey != null) {
            log.debug("根据实体 {} 切换到数据源: {}", entityName, dataSourceKey);
            return dataSourceKey;
        }
        log.debug("实体 {} 未找到数据源映射，使用默认数据源", entityName);
        return resolveDefaultDataSourceKey();
    }

    /**
     * 解析默认数据源 key（非实体路由时的兜底链）。
     * <p>注解作用域值 &gt; 外层已显式设置的 key（原样返回，保护 withDataSource /
     * switchDbByConnectId 等嵌套切库语义，设置同值等价于保持原值）&gt;
     * 平台默认 key（启动时由 OrmAutoConfiguration 写入）&gt; primary 硬兜底。
     */
    private String resolveDefaultDataSourceKey() {
        String annotationDefault = DEFAULT_DATA_SOURCE.get();
        if (annotationDefault != null) {
            return annotationDefault;
        }
        String outerScopeKey = DynamicDataSourceHolder.getDataSourceKey();
        if (outerScopeKey != null && !outerScopeKey.trim().isEmpty()) {
            return outerScopeKey;
        }
        String platformDefault = DataSourceManager.singleInstance().getDefaultDataSourceKey();
        return platformDefault == null || platformDefault.trim().isEmpty()
                ? PRIMARY_DATASOURCE_KEY : platformDefault;
    }

    private void applyDataSourceKey(String dataSourceKey) {
        if (dataSourceKey != null) {
            DynamicDataSourceHolder.setDataSourceKey(dataSourceKey);
        }
    }

    private void restorePreviousDataSource(String previous) {
        if (previous != null) {
            DynamicDataSourceHolder.setDataSourceKey(previous);
        } else {
            DynamicDataSourceHolder.clearDataSourceKey();
        }
    }
}
