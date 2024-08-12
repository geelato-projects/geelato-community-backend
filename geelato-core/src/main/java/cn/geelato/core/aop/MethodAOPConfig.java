package cn.geelato.core.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Aspect
@Component
public class MethodAOPConfig {

    @Around(value = "@annotation(cn.geelato.core.aop.annotation.MethodLog)")
    public Object around(ProceedingJoinPoint proceedingJoinPoint){
        System.out.println("method around...args:"+ Arrays.toString(proceedingJoinPoint.getArgs()));
        try {
            Object ret= proceedingJoinPoint.proceed();
            System.out.println("method around...result:"+ret);
            return ret;
        } catch (Throwable throwable) {
            
            throwable.printStackTrace();
        }
        return null;
    }

    @Before(value = "@annotation(cn.geelato.core.aop.annotation.MethodLog)")
    public void doBefore(JoinPoint joinPoint){
        System.out.println("method name:" +joinPoint.getSignature().getName());
        System.out.println("method exec before:" + Arrays.toString(joinPoint.getArgs()));
    }

    @After(value = "@annotation(cn.geelato.core.aop.annotation.MethodLog)")
    public void after(JoinPoint joinPoint){
        System.out.println("method exec after:"+ Arrays.toString(joinPoint.getArgs()));
    }

    @AfterReturning(pointcut = "@annotation(cn.geelato.core.aop.annotation.MethodLog)",returning = "ret")
    public void doAfterReturning(Object ret){
        System.out.println("method return result:" + ret);
    }

    @AfterThrowing(pointcut = "@annotation(cn.geelato.core.aop.annotation.MethodLog)",throwing = "ex")
    public void AfterThrowing(JoinPoint joinPoint,Throwable ex){
        System.out.println("method fail:" + ex);
    }
}