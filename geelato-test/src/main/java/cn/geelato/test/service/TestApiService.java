package cn.geelato.test.service;

import cn.geelato.test.annotation.GeelatoTest;
import cn.geelato.test.loader.TestCaseLoader;
import cn.geelato.test.model.TestApiInfo;
import cn.geelato.test.model.TestCase;
import cn.geelato.test.model.TestCaseItem;
import cn.geelato.test.model.TestResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 测试接口服务
 * 负责管理测试接口清单
 */
@Slf4j
@Service
public class TestApiService {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private TestResultStorage testResultStorage;

    /**
     * 获取所有测试接口清单
     *
     * @return 测试接口清单列表
     */
    public List<TestApiInfo> getAllTestApis() {
        List<TestApiInfo> apiInfoList = new ArrayList<>();

        // 获取所有带有@RestController注解的Bean
        Map<String, Object> controllers = applicationContext.getBeansWithAnnotation(RestController.class);

        for (Object controller : controllers.values()) {
            Class<?> controllerClass = controller.getClass();
            // 处理代理类
            if (controllerClass.getName().contains("CGLIB")) {
                controllerClass = controllerClass.getSuperclass();
            }

            // 获取控制器的基础URL
            String baseUrl = "";
            if (controllerClass.isAnnotationPresent(RequestMapping.class)) {
                RequestMapping requestMapping = controllerClass.getAnnotation(RequestMapping.class);
                if (requestMapping.value().length > 0) {
                    baseUrl = requestMapping.value()[0];
                }
            }

            // 遍历所有方法
            Method[] methods = ReflectionUtils.getAllDeclaredMethods(controllerClass);
            for (Method method : methods) {
                if (method.isAnnotationPresent(GeelatoTest.class)) {
                    TestApiInfo apiInfo = createTestApiInfo(controllerClass, method, baseUrl);
                    apiInfoList.add(apiInfo);
                }
            }
        }

        return apiInfoList;
    }

    /**
     * 获取指定类的测试接口清单
     *
     * @param className 类名
     * @return 测试接口清单列表
     */
    public List<TestApiInfo> getTestApisByClass(String className) {
        List<TestApiInfo> apiInfoList = new ArrayList<>();

        try {
            Class<?> clazz = Class.forName(className);
            String baseUrl = "";
            if (clazz.isAnnotationPresent(RequestMapping.class)) {
                RequestMapping requestMapping = clazz.getAnnotation(RequestMapping.class);
                if (requestMapping.value().length > 0) {
                    baseUrl = requestMapping.value()[0];
                }
            }

            Method[] methods = ReflectionUtils.getAllDeclaredMethods(clazz);
            for (Method method : methods) {
                if (method.isAnnotationPresent(GeelatoTest.class)) {
                    TestApiInfo apiInfo = createTestApiInfo(clazz, method, baseUrl);
                    apiInfoList.add(apiInfo);
                }
            }
        } catch (ClassNotFoundException e) {
            log.error("类不存在: {}", className, e);
        }

        return apiInfoList;
    }

    /**
     * 创建测试接口信息
     *
     * @param clazz    类
     * @param method   方法
     * @param baseUrl  基础URL
     * @return 测试接口信息
     */
    private TestApiInfo createTestApiInfo(Class<?> clazz, Method method, String baseUrl) {
        TestApiInfo apiInfo = new TestApiInfo();
        apiInfo.setClassName(clazz.getName());
        apiInfo.setMethodName(method.getName());

        // 设置服务名称
        GeelatoTest geelatoTest = method.getAnnotation(GeelatoTest.class);
        apiInfo.setServiceName(geelatoTest.description());

        // 设置服务地址
        String methodUrl = getMethodUrl(method);
        apiInfo.setServiceUrl(baseUrl + methodUrl);

        // 加载测试用例
        TestCase testCase = TestCaseLoader.loadTestCase(clazz, method);
        if (testCase != null && !testCase.getTestItems().isEmpty()) {
            TestCaseItem firstItem = testCase.getTestItems().get(0);
            apiInfo.setTestInput(firstItem.getInput());
            apiInfo.setExpectedResult(firstItem.getExpectedOutput());
        }

        // 获取最新测试结果
        TestResult latestResult = testResultStorage.getLatestTestResult(clazz.getName(), method.getName());
        if (latestResult != null) {
            apiInfo.setLastTestTime(latestResult.getEndTime());
            apiInfo.setPassed(latestResult.isAllPassed());
            apiInfo.setTestResult(latestResult.isAllPassed() ? "通过" : "失败");
        }

        return apiInfo;
    }

    /**
     * 获取方法的URL
     *
     * @param method 方法
     * @return URL
     */
    private String getMethodUrl(Method method) {
        // 支持各种Spring MVC的注解
        if (method.isAnnotationPresent(RequestMapping.class)) {
            RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
            if (requestMapping.value().length > 0) {
                return requestMapping.value()[0];
            }
        }

        // 可以添加对GetMapping, PostMapping等注解的支持
        // 这里简化处理，直接返回方法名
        return "/" + method.getName();
    }
}