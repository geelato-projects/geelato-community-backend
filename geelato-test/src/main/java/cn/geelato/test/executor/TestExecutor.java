package cn.geelato.test.executor;

import cn.geelato.test.annotation.GeelatoTest;
import cn.geelato.test.loader.TestCaseLoader;
import cn.geelato.test.model.TestCase;
import cn.geelato.test.model.TestCaseItem;
import cn.geelato.test.model.TestResult;
import cn.geelato.test.service.TestResultStorage;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 测试执行器
 * 负责执行测试用例并生成测试结果
 */
@Slf4j
@Component
public class TestExecutor {

    @Autowired
    private ApplicationContext applicationContext;
    
    @Autowired
    private TestResultStorage testResultStorage;

    /**
     * 执行指定类和方法的测试
     *
     * @param className  类名
     * @param methodName 方法名
     * @return 测试结果
     */
    public TestResult executeTest(String className, String methodName) {
        try {
            Class<?> clazz = Class.forName(className);
            Method method = findMethod(clazz, methodName);
            
            if (method == null) {
                throw new IllegalArgumentException("方法不存在: " + className + "." + methodName);
            }
            
            if (!method.isAnnotationPresent(GeelatoTest.class)) {
                throw new IllegalArgumentException("方法未标注@GeelatoTest注解: " + className + "." + methodName);
            }
            
            return executeTest(clazz, method);
        } catch (Exception e) {
            log.error("执行测试失败: {}.{}", className, methodName, e);
            TestResult result = new TestResult();
            result.setId(UUID.randomUUID().toString());
            result.setClassName(className);
            result.setMethodName(methodName);
            result.setName(methodName + "测试");
            result.setDescription("测试执行失败");
            result.setStartTime(new Date());
            result.setEndTime(new Date());
            result.setDuration(0);
            result.setTotalCount(0);
            result.setPassedCount(0);
            result.setFailedCount(0);
            result.setAllPassed(false);
            result.setTestItems(new ArrayList<>());
            return result;
        }
    }

    /**
     * 执行指定类和方法的测试
     *
     * @param clazz  类
     * @param method 方法
     * @return 测试结果
     */
    public TestResult executeTest(Class<?> clazz, Method method) {
        TestCase testCase = TestCaseLoader.loadTestCase(clazz, method);
        TestResult result = new TestResult();
        result.setId(UUID.randomUUID().toString());
        result.setClassName(testCase.getClassName());
        result.setMethodName(testCase.getMethodName());
        result.setName(testCase.getName());
        result.setDescription(testCase.getDescription());
        
        Date startTime = new Date();
        result.setStartTime(startTime);
        
        List<TestCaseItem> testItems = testCase.getTestItems();
        result.setTotalCount(testItems.size());
        
        if (testItems.isEmpty()) {
            log.warn("测试用例为空: {}.{}", clazz.getName(), method.getName());
            result.setEndTime(new Date());
            result.setDuration(0);
            result.setPassedCount(0);
            result.setFailedCount(0);
            result.setAllPassed(true);
            result.setTestItems(new ArrayList<>());
            return result;
        }
        
        Object bean = applicationContext.getBean(clazz);
        int passedCount = 0;
        int failedCount = 0;
        
        for (TestCaseItem item : testItems) {
            try {
                // 解析输入参数
                Object[] args = parseInputParams(method, item.getInput());
                
                // 执行方法
                Object result1 = ReflectionUtils.invokeMethod(method, bean, args);
                
                // 转换结果为JSON字符串
                String actualOutput = JSON.toJSONString(result1);
                item.setActualOutput(actualOutput);
                
                // 比较结果
                boolean passed = compareResult(item.getExpectedOutput(), actualOutput);
                item.setPassed(passed);
                
                if (passed) {
                    passedCount++;
                } else {
                    failedCount++;
                    item.setErrorMessage("实际结果与预期结果不匹配");
                }
            } catch (Exception e) {
                log.error("执行测试项失败: {}", item.getId(), e);
                item.setPassed(false);
                item.setErrorMessage(e.getMessage());
                failedCount++;
            }
        }
        
        Date endTime = new Date();
        result.setEndTime(endTime);
        result.setDuration(endTime.getTime() - startTime.getTime());
        result.setPassedCount(passedCount);
        result.setFailedCount(failedCount);
        result.setAllPassed(failedCount == 0);
        result.setTestItems(testItems);
        
        // 保存测试结果
        String savedPath = testResultStorage.saveTestResult(result);
        log.info("测试结果已保存到: {}", savedPath);
        
        return result;
    }

    /**
     * 查找方法
     *
     * @param clazz      类
     * @param methodName 方法名
     * @return 方法
     */
    private Method findMethod(Class<?> clazz, String methodName) {
        Method[] methods = ReflectionUtils.getAllDeclaredMethods(clazz);
        for (Method method : methods) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        return null;
    }

    /**
     * 解析输入参数
     *
     * @param method 方法
     * @param input  输入参数JSON字符串
     * @return 参数数组
     */
    private Object[] parseInputParams(Method method, String input) {
        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length == 0) {
            return new Object[0];
        }
        
        JSONObject jsonObject = JSON.parseObject(input);
        Object[] args = new Object[paramTypes.length];
        
        for (int i = 0; i < paramTypes.length; i++) {
            String paramName = "param" + i;
            if (jsonObject.containsKey(paramName)) {
                args[i] = jsonObject.getObject(paramName, paramTypes[i]);
            } else {
                args[i] = null;
            }
        }
        
        return args;
    }

    /**
     * 比较结果
     *
     * @param expected 预期结果
     * @param actual   实际结果
     * @return 是否匹配
     */
    private boolean compareResult(String expected, String actual) {
        try {
            JSONObject expectedJson = JSON.parseObject(expected);
            JSONObject actualJson = JSON.parseObject(actual);
            return expectedJson.equals(actualJson);
        } catch (Exception e) {
            // 如果不是JSON格式，直接比较字符串
            return expected.equals(actual);
        }
    }
}