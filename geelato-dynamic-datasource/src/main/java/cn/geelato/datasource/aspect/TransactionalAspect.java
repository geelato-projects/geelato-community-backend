package cn.geelato.datasource.aspect;

import cn.geelato.datasource.annotion.UseTransactional;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 事务切面
 * 处理@UseTransactional注解，为标记的Dao字段的方法自动添加事务支持
 */
@Aspect
@Component
public class TransactionalAspect {
    
    private static final Logger logger = LoggerFactory.getLogger(TransactionalAspect.class);
    
    @Autowired
    private PlatformTransactionManager transactionManager;
    
    /**
     * 拦截所有Dao方法的调用
     * 检查调用方是否有@UseTransactional注解的字段
     */
    @Around("execution(* cn.geelato.core.orm.Dao.*(..))")
    public Object aroundDaoMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        Object target = joinPoint.getTarget();
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        
        // 检查是否需要事务支持
        UseTransactional useTransactional = findUseTransactionalAnnotation(target);
        if (useTransactional == null) {
            return joinPoint.proceed();
        }
        if (method.isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class)) {
            return joinPoint.proceed();
        }
        DefaultTransactionDefinition transactionDefinition = createTransactionDefinition(useTransactional);
        TransactionStatus transactionStatus = transactionManager.getTransaction(transactionDefinition);
        try {
            logger.debug("开始事务: {}.{}", target.getClass().getSimpleName(), method.getName());
            Object result = joinPoint.proceed();
            transactionManager.commit(transactionStatus);
            logger.debug("提交事务: {}.{}", target.getClass().getSimpleName(), method.getName());
            return result;
            
        } catch (Exception e) {
            if (shouldRollback(e, useTransactional)) {
                transactionManager.rollback(transactionStatus);
                logger.debug("回滚事务: {}.{}, 异常: {}", target.getClass().getSimpleName(), method.getName(), e.getMessage());
            } else {
                transactionManager.commit(transactionStatus);
                logger.debug("提交事务(忽略异常): {}.{}, 异常: {}", target.getClass().getSimpleName(), method.getName(), e.getMessage());
            }
            throw e;
        }
    }
    
    /**
     * 查找@UseTransactional注解
     * 通过反射检查调用栈中是否有标记了@UseTransactional的字段
     */
    private UseTransactional findUseTransactionalAnnotation(Object daoTarget) {
        try {
            // 获取当前线程的调用栈
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            
            for (StackTraceElement element : stackTrace) {
                try {
                    Class<?> callerClass = Class.forName(element.getClassName());
                    
                    // 检查调用类的所有字段
                    for (Field field : callerClass.getDeclaredFields()) {
                        if (field.isAnnotationPresent(UseTransactional.class)) {
                            // 检查字段类型是否与当前Dao匹配
                            if (field.getType().isAssignableFrom(daoTarget.getClass())) {
                                return field.getAnnotation(UseTransactional.class);
                            }
                        }
                    }
                } catch (ClassNotFoundException e) {
                    // 忽略无法加载的类
                }
            }
        } catch (Exception e) {
            logger.warn("查找@UseTransactional注解时发生错误: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 创建事务定义
     */
    private DefaultTransactionDefinition createTransactionDefinition(UseTransactional useTransactional) {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        
        // 设置传播行为
        switch (useTransactional.propagation()) {
            case "REQUIRED":
                definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
                break;
            case "REQUIRES_NEW":
                definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                break;
            case "SUPPORTS":
                definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_SUPPORTS);
                break;
            case "NOT_SUPPORTED":
                definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
                break;
            case "NEVER":
                definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_NEVER);
                break;
            case "MANDATORY":
                definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_MANDATORY);
                break;
            case "NESTED":
                definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
                break;
            default:
                definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        }
        
        // 设置隔离级别
        switch (useTransactional.isolation()) {
            case "READ_UNCOMMITTED":
                definition.setIsolationLevel(TransactionDefinition.ISOLATION_READ_UNCOMMITTED);
                break;
            case "READ_COMMITTED":
                definition.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
                break;
            case "REPEATABLE_READ":
                definition.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
                break;
            case "SERIALIZABLE":
                definition.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
                break;
            default:
                definition.setIsolationLevel(TransactionDefinition.ISOLATION_DEFAULT);
        }
        
        // 设置超时时间
        if (useTransactional.timeout() > 0) {
            definition.setTimeout(useTransactional.timeout());
        }
        
        // 设置只读
        definition.setReadOnly(useTransactional.readOnly());
        
        return definition;
    }
    
    /**
     * 判断是否应该回滚事务
     */
    private boolean shouldRollback(Exception exception, UseTransactional useTransactional) {
        // 检查rollbackFor
        for (Class<? extends Throwable> rollbackClass : useTransactional.rollbackFor()) {
            if (rollbackClass.isAssignableFrom(exception.getClass())) {
                return true;
            }
        }
        
        // 检查rollbackForClassName
        for (String className : useTransactional.rollbackForClassName()) {
            try {
                Class<?> rollbackClass = Class.forName(className);
                if (rollbackClass.isAssignableFrom(exception.getClass())) {
                    return true;
                }
            } catch (ClassNotFoundException e) {
                // 忽略无法加载的类
            }
        }
        
        // 检查noRollbackFor
        for (Class<? extends Throwable> noRollbackClass : useTransactional.noRollbackFor()) {
            if (noRollbackClass.isAssignableFrom(exception.getClass())) {
                return false;
            }
        }
        
        // 检查noRollbackForClassName
        for (String className : useTransactional.noRollbackForClassName()) {
            try {
                Class<?> noRollbackClass = Class.forName(className);
                if (noRollbackClass.isAssignableFrom(exception.getClass())) {
                    return false;
                }
            } catch (ClassNotFoundException e) {
                // 忽略无法加载的类
            }
        }
        
        // 默认情况：RuntimeException和Error会回滚
        return (exception instanceof RuntimeException);
    }
}