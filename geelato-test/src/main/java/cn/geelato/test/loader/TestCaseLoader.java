package cn.geelato.test.loader;

import cn.geelato.test.model.TestCase;
import cn.geelato.test.model.TestCaseItem;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 测试用例加载器
 * 负责从资源文件中加载测试用例
 */
@Slf4j
public class TestCaseLoader {

    private static final String TEST_CASE_PATH = "classpath:testcases/";
    private static final String TEST_CASE_SUFFIX = ".json";

    /**
     * 加载指定类和方法的测试用例
     *
     * @param clazz  类
     * @param method 方法
     * @return 测试用例
     */
    public static TestCase loadTestCase(Class<?> clazz, Method method) {
        String className = clazz.getName();
        String methodName = method.getName();
        String testCasePath = generateTestCasePath(className, methodName);
        
        try {
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource resource = resolver.getResource(testCasePath);
            
            if (resource.exists()) {
                return parseTestCase(resource, className, methodName);
            } else {
                log.warn("测试用例文件不存在: {}", testCasePath);
                return createEmptyTestCase(className, methodName);
            }
        } catch (Exception e) {
            log.error("加载测试用例失败: {}", testCasePath, e);
            return createEmptyTestCase(className, methodName);
        }
    }

    /**
     * 解析测试用例文件
     *
     * @param resource   资源文件
     * @param className  类名
     * @param methodName 方法名
     * @return 测试用例
     */
    private static TestCase parseTestCase(Resource resource, String className, String methodName) throws IOException {
        try (InputStream inputStream = resource.getInputStream()) {
            String content = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
            JSONObject jsonObject = JSON.parseObject(content);
            
            TestCase testCase = new TestCase();
            testCase.setClassName(className);
            testCase.setMethodName(methodName);
            testCase.setName(jsonObject.getString("name"));
            testCase.setDescription(jsonObject.getString("description"));
            
            List<TestCaseItem> testItems = new ArrayList<>();
            for (Object item : jsonObject.getJSONArray("testItems")) {
                JSONObject itemJson = (JSONObject) item;
                TestCaseItem testItem = new TestCaseItem();
                testItem.setId(itemJson.getString("id"));
                testItem.setDescription(itemJson.getString("description"));
                testItem.setInput(itemJson.getString("input"));
                testItem.setExpectedOutput(itemJson.getString("expectedOutput"));
                testItems.add(testItem);
            }
            
            testCase.setTestItems(testItems);
            return testCase;
        }
    }

    /**
     * 创建空的测试用例
     *
     * @param className  类名
     * @param methodName 方法名
     * @return 空的测试用例
     */
    private static TestCase createEmptyTestCase(String className, String methodName) {
        TestCase testCase = new TestCase();
        testCase.setClassName(className);
        testCase.setMethodName(methodName);
        testCase.setName(methodName + "测试");
        testCase.setDescription(className + "." + methodName + "的测试用例");
        testCase.setTestItems(new ArrayList<>());
        return testCase;
    }

    /**
     * 生成测试用例文件路径
     *
     * @param className  类名
     * @param methodName 方法名
     * @return 测试用例文件路径
     */
    public static String generateTestCasePath(String className, String methodName) {
        return TEST_CASE_PATH + className.replace('.', '/') + "/" + methodName + TEST_CASE_SUFFIX;
    }
}