package cn.geelato.web.platform.run.monitor.schedule;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
public class ScheduledTaskRuntimeAspect {
    private final ScheduledTaskRuntimeTracker runtimeTracker;

    public ScheduledTaskRuntimeAspect(ScheduledTaskRuntimeTracker runtimeTracker) {
        this.runtimeTracker = runtimeTracker;
    }

    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    public Object traceScheduledTask(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Class<?> targetClass = resolveTargetClass(joinPoint, signature);
        Method method = signature.getMethod();
        Method specificMethod = targetClass == null ? method : targetClass.getMethod(method.getName(), method.getParameterTypes());
        String taskId = ScheduledTaskMonitorRegistry.buildTaskId(targetClass == null ? method.getDeclaringClass() : targetClass, specificMethod);
        long startedAt = System.currentTimeMillis();
        runtimeTracker.markStart(taskId, Thread.currentThread().getName(), startedAt);
        try {
            Object result = joinPoint.proceed();
            long finishedAt = System.currentTimeMillis();
            runtimeTracker.markSuccess(taskId, finishedAt, finishedAt - startedAt);
            return result;
        } catch (Throwable throwable) {
            long finishedAt = System.currentTimeMillis();
            runtimeTracker.markFailure(taskId, finishedAt, finishedAt - startedAt, throwable);
            throw throwable;
        }
    }

    private Class<?> resolveTargetClass(ProceedingJoinPoint joinPoint, MethodSignature signature) {
        Object target = joinPoint.getTarget();
        if (target != null) {
            Class<?> targetClass = AopProxyUtils.ultimateTargetClass(target);
            if (targetClass != null) {
                return targetClass;
            }
        }
        return signature.getDeclaringType();
    }
}
