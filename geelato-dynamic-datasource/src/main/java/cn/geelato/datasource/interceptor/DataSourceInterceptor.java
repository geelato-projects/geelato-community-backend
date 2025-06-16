package cn.geelato.datasource.interceptor;

import cn.geelato.core.gql.execute.BoundPageSql;
import cn.geelato.core.gql.execute.BoundSql;
import cn.geelato.datasource.DynamicDataSourceHolder;
import cn.geelato.datasource.EntityDataSourceResolver;
import cn.geelato.datasource.annotion.UseDynamicDataSource;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    @Before("@within(cn.geelato.datasource.annotion.UseDynamicDataSource) " +
            "|| @annotation(cn.geelato.datasource.annotion.UseDynamicDataSource)")
    public void beforeTransaction(JoinPoint point) {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();

        UseDynamicDataSource dataSourceAnnotation = method.getAnnotation(UseDynamicDataSource.class);
        if (dataSourceAnnotation == null) {
            dataSourceAnnotation = AnnotationUtils.findAnnotation(
                    method.getDeclaringClass(), UseDynamicDataSource.class);
        }

        if (dataSourceAnnotation != null) {
            String defaultDataSource = dataSourceAnnotation.value();
            DEFAULT_DATA_SOURCE.set(defaultDataSource);
            log.debug("设置默认数据源: {}", defaultDataSource);
        }
    }

    /**
     * 事务结束后的清理工作
     */
    @After("(@within(cn.geelato.datasource.annotion.UseDynamicDataSource) " +
            "|| @annotation(cn.geelato.datasource.annotion.UseDynamicDataSource))" +
            " && @annotation(org.springframework.transaction.annotation.Transactional)")
    public void afterTransaction(JoinPoint point) {
        DEFAULT_DATA_SOURCE.remove();
        DynamicDataSourceHolder.clearDataSourceKey();
        log.debug("清理数据源上下文");
    }

    /**
     * DAO方法执行前的数据源切换
     */
    @Before("execution(* cn.geelato.core.orm.Dao.*(..)) && args(boundPageSql)")
    public void beforeDaoMethod(JoinPoint point, BoundPageSql boundPageSql) {
        if (boundPageSql == null || boundPageSql.getBoundSql().getCommand().getEntityName() == null) {
            String defaultSource = DEFAULT_DATA_SOURCE.get();
            if (defaultSource != null) {
                DynamicDataSourceHolder.setDataSourceKey(defaultSource);
                log.debug("使用默认数据源: {}", defaultSource);
            }
            return;
        }

        String entityName =  boundPageSql.getBoundSql().getCommand().getEntityName() ;
        String dataSource =entityDataSourceResolver.resolveDataSource(entityName);

        if (dataSource != null) {
            DynamicDataSourceHolder.setDataSourceKey(dataSource);
            log.debug("根据实体 {} 切换到数据源: {}", entityName, dataSource);
        } else {
            String defaultSource = DEFAULT_DATA_SOURCE.get();
            if (defaultSource != null) {
                DynamicDataSourceHolder.setDataSourceKey(defaultSource);
                log.debug("实体 {} 未找到映射，使用默认数据源: {}", entityName, defaultSource);
            }
        }
    }

    @Before("execution(* cn.geelato.core.orm.Dao.*(..)) && args(boundSql)")
    public void beforeDaoMethod(JoinPoint point, BoundSql boundSql) {
        if (boundSql == null || boundSql.getCommand().getEntityName() == null) {
            String defaultSource = DEFAULT_DATA_SOURCE.get();
            if (defaultSource != null) {
                DynamicDataSourceHolder.setDataSourceKey(defaultSource);
                log.debug("使用默认数据源: {}", defaultSource);
            }
            return;
        }

        String entityName =  boundSql.getCommand().getEntityName() ;
        String dataSource =entityDataSourceResolver.resolveDataSource(entityName);

        if (dataSource != null) {
            DynamicDataSourceHolder.setDataSourceKey(dataSource);
            log.debug("根据实体 {} 切换到数据源: {}", entityName, dataSource);
        } else {
            String defaultSource = DEFAULT_DATA_SOURCE.get();
            if (defaultSource != null) {
                DynamicDataSourceHolder.setDataSourceKey(defaultSource);
                log.debug("实体 {} 未找到映射，使用默认数据源: {}", entityName, defaultSource);
            }
        }
    }
}