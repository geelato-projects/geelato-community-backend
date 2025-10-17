package cn.geelato.test.aop;

import cn.geelato.test.annotation.GeelatoTest;
import cn.geelato.test.datasource.TestDataSourceSwitcher;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

/**
 * 测试数据源切换AOP
 * 在执行带有@GeelatoTest注解的方法前，切换到测试数据源
 * 在执行完成后，切换回默认数据源
 */
@Slf4j
@Aspect
@Component
public class TestDataSourceAspect {

    /**
     * 定义切点：所有带有@GeelatoTest注解的方法
     */
    @Pointcut("@annotation(cn.geelato.test.annotation.GeelatoTest)")
    public void testPointcut() {
    }

    /**
     * 环绕通知：在方法执行前后切换数据源
     *
     * @param joinPoint 连接点
     * @return 方法执行结果
     * @throws Throwable 异常
     */
    @Around("testPointcut() && @annotation(geelatoTest)")
    public Object around(ProceedingJoinPoint joinPoint, GeelatoTest geelatoTest) throws Throwable {
        return joinPoint.proceed();
        // 如果测试未启用，直接执行方法
//        if (!geelatoTest.enabled()) {
//            return joinPoint.proceed();
//        }
//
//        // 获取当前数据源
//        String originalDataSource = TestDataSourceSwitcher.getDataSource();
//
//        try {
//            // 切换到测试数据源
//            log.info("切换到测试数据源");
//            TestDataSourceSwitcher.switchToTestDataSource();
//
//            // 执行方法
//            return joinPoint.proceed();
//        } finally {
//            // 恢复原数据源
//            log.info("恢复原数据源: {}", originalDataSource);
//            TestDataSourceSwitcher.setDataSource(originalDataSource);
//        }
    }
}