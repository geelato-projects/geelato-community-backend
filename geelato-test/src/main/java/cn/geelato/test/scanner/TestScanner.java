package cn.geelato.test.scanner;

import cn.geelato.test.annotation.GeelatoTest;
import cn.geelato.test.executor.TestExecutor;
import cn.geelato.test.model.TestResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 测试扫描器
 * 用于扫描带有@GeelatoTest注解的方法并执行测试
 */
@Slf4j
@Component
public class TestScanner {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private TestExecutor testExecutor;

    /**
     * 扫描并执行指定类的所有测试
     *
     * @param className 类名
     * @return 测试结果列表
     */
    public List<TestResult> scanAndExecuteClassTests(String className) {
        List<TestResult> results = new ArrayList<>();
        try {
            Class<?> clazz = Class.forName(className);
            scanAndExecuteClassTests(clazz, results);
        } catch (Exception e) {
            log.error("扫描类测试失败: {}", className, e);
        }
        return results;
    }

    /**
     * 扫描并执行指定类的所有测试
     *
     * @param clazz   类
     * @param results 测试结果列表
     */
    private void scanAndExecuteClassTests(Class<?> clazz, List<TestResult> results) {
        Method[] methods = ReflectionUtils.getAllDeclaredMethods(clazz);
        for (Method method : methods) {
            if (method.isAnnotationPresent(GeelatoTest.class)) {
                GeelatoTest geelatoTest = method.getAnnotation(GeelatoTest.class);
                if (geelatoTest.enabled()) {
                    log.info("执行测试: {}.{}", clazz.getName(), method.getName());
                    TestResult result = testExecutor.executeTest(clazz, method);
                    results.add(result);
                }
            }
        }
    }

    /**
     * 扫描并执行指定包的所有测试
     *
     * @param packageName 包名
     * @return 测试结果列表
     */
    public List<TestResult> scanAndExecutePackageTests(String packageName) {
        List<TestResult> results = new ArrayList<>();
        try {
            ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(false);
            provider.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
            provider.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
            provider.addIncludeFilter(new AnnotationTypeFilter(Service.class));
            
            Set<BeanDefinition> beans = provider.findCandidateComponents(packageName);
            for (BeanDefinition bean : beans) {
                String beanClassName = bean.getBeanClassName();
                try {
                    Class<?> clazz = Class.forName(beanClassName);
                    scanAndExecuteClassTests(clazz, results);
                } catch (Exception e) {
                    log.error("扫描类测试失败: {}", beanClassName, e);
                }
            }
        } catch (Exception e) {
            log.error("扫描包测试失败: {}", packageName, e);
        }
        return results;
    }

    /**
     * 扫描并执行所有测试
     *
     * @return 测试结果列表
     */
    public List<TestResult> scanAndExecuteAllTests() {
        List<TestResult> results = new ArrayList<>();
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            Object bean = applicationContext.getBean(beanName);
            Class<?> clazz = bean.getClass();
            scanAndExecuteClassTests(clazz, results);
        }
        return results;
    }
}