package cn.geelato.datasource.interceptor;

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
     * 事务开始前的数据源设置
     * 拦截被@UseDynamicDataSource注解标注的类中的所有方法
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

    private String resolveEntityName(Object[] args) {
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
                Entity entityAnnotation = clazz.getAnnotation(Entity.class);
                return entityAnnotation.name().isEmpty() ? clazz.getSimpleName() : entityAnnotation.name();
            }
        }
        return null;
    }

    private String resolveDataSourceKey(String entityName) {
        String defaultSource = DEFAULT_DATA_SOURCE.get();
        if (entityName == null) {
            if (defaultSource != null) {
                log.debug("使用默认数据源: {}", defaultSource);
            }
            return defaultSource;
        }

        String dataSourceKey = entityDataSourceResolver.resolveDataSource(entityName);
        if (dataSourceKey != null) {
            log.debug("根据实体 {} 切换到数据源: {}", entityName, dataSourceKey);
            return dataSourceKey;
        }
        if (defaultSource != null) {
            log.debug("实体 {} 未找到映射，使用默认数据源: {}", entityName, defaultSource);
        }
        return defaultSource;
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
