package cn.geelato.datasource.aspect;

import cn.geelato.datasource.annotation.UseTransactional;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.lang.reflect.Method;

/**
 * 事务切面
 * <p>处理 @UseTransactional 注解，为标记的 Dao 字段的方法自动添加事务支持。</p>
 * <p><strong>当前状态：未激活。</strong>
 * aroundDaoMethod 的 @Around 注解已移除，切面不会拦截任何方法调用。
 * 如需启用，请为 aroundDaoMethod 方法添加合适的 @Around 注解。</p>
 */
@Aspect
@Component
@ConditionalOnBean(name = "dynamicDataSourceTransactionManager")
public class TransactionalAspect {

    private static final Logger logger = LoggerFactory.getLogger(TransactionalAspect.class);
    
    @Autowired
    @Qualifier("dynamicDataSourceTransactionManager")
    private PlatformTransactionManager transactionManager;

    /**
     * 拦截 Dao 方法的调用，为其添加事务支持
     * <p>当前未激活：需添加 @Around 注解才能生效</p>
     */
    public Object aroundDaoMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        Object target = joinPoint.getTarget();
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();

        if (method.isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class)) {
            return joinPoint.proceed();
        }

        DefaultTransactionDefinition transactionDefinition = createTransactionDefinition();
        TransactionStatus transactionStatus = transactionManager.getTransaction(transactionDefinition);
        try {
            logger.debug("开始事务: {}.{}", target.getClass().getSimpleName(), method.getName());
            Object result = joinPoint.proceed();
            transactionManager.commit(transactionStatus);
            logger.debug("提交事务: {}.{}", target.getClass().getSimpleName(), method.getName());
            return result;
        } catch (Exception e) {
            transactionManager.rollback(transactionStatus);
            logger.debug("回滚事务: {}.{}, 异常: {}", target.getClass().getSimpleName(), method.getName(), e.getMessage());
            throw e;
        }
    }

    /**
     * 创建默认事务定义
     */
    private DefaultTransactionDefinition createTransactionDefinition() {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        definition.setIsolationLevel(TransactionDefinition.ISOLATION_READ_UNCOMMITTED);
        definition.setTimeout(30000);
        definition.setReadOnly(true);
        return definition;
    }
}
