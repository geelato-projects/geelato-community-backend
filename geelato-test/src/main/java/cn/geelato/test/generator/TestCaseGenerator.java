package cn.geelato.test.generator;

import cn.geelato.test.annotation.GeelatoTest;
import cn.geelato.test.model.TestCase;
import cn.geelato.test.model.TestCaseItem;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 测试用例生成器
 * 用于自动生成测试用例文件
 */
@Slf4j
@Component
public class TestCaseGenerator {

    private static final String TEST_CASE_DIR = "src/main/resources/testcases/";

    /**
     * 为指定类的所有带有@GeelatoTest注解的方法生成测试用例
     *
     * @param clazz 类
     * @return 生成的测试用例文件路径列表
     */
    public List<String> generateTestCases(Class<?> clazz) {
        List<String> generatedFiles = new ArrayList<>();
        Method[] methods = ReflectionUtils.getAllDeclaredMethods(clazz);
        
        for (Method method : methods) {
            if (method.isAnnotationPresent(GeelatoTest.class)) {
                String filePath = generateTestCase(clazz, method);
                if (filePath != null) {
                    generatedFiles.add(filePath);
                }
            }
        }
        
        return generatedFiles;
    }

    /**
     * 为指定方法生成测试用例
     *
     * @param clazz  类
     * @param method 方法
     * @return 生成的测试用例文件路径
     */
    public String generateTestCase(Class<?> clazz, Method method) {
        try {
            GeelatoTest annotation = method.getAnnotation(GeelatoTest.class);
            if (annotation == null) {
                return null;
            }
            
            String className = clazz.getName();
            String methodName = method.getName();
            
            // 创建测试用例对象
            TestCase testCase = new TestCase();
            testCase.setClassName(className);
            testCase.setMethodName(methodName);
            testCase.setName(StringUtils.hasText(annotation.description()) ? annotation.description() : methodName + "测试");
            testCase.setDescription(className + "." + methodName + "的测试用例");
            
            // 创建测试用例项
            List<TestCaseItem> testItems = new ArrayList<>();
            TestCaseItem item = createDefaultTestItem(method);
            testItems.add(item);
            testCase.setTestItems(testItems);
            
            // 生成测试用例文件
            String filePath = getTestCaseFilePath(className, methodName);
            writeTestCaseFile(testCase, filePath);
            
            return filePath;
        } catch (Exception e) {
            log.error("生成测试用例失败: {}.{}", clazz.getName(), method.getName(), e);
            return null;
        }
    }

    /**
     * 创建默认测试用例项
     *
     * @param method 方法
     * @return 测试用例项
     */
    private TestCaseItem createDefaultTestItem(Method method) {
        TestCaseItem item = new TestCaseItem();
        item.setId(UUID.randomUUID().toString());
        item.setDescription("默认测试用例");
        
        // 创建默认输入参数
        JSONObject inputJson = new JSONObject();
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            inputJson.put("param" + i, createDefaultValue(parameters[i].getType()));
        }
        item.setInput(inputJson.toString());
        
        // 创建默认预期输出
        JSONObject outputJson = new JSONObject();
        outputJson.put("success", true);
        outputJson.put("message", "操作成功");
        item.setExpectedOutput(outputJson.toString());
        
        return item;
    }

    /**
     * 创建默认值
     *
     * @param type 类型
     * @return 默认值
     */
    private Object createDefaultValue(Class<?> type) {
        if (type == String.class) {
            return "";
        } else if (type == Integer.class || type == int.class) {
            return 0;
        } else if (type == Long.class || type == long.class) {
            return 0L;
        } else if (type == Double.class || type == double.class) {
            return 0.0;
        } else if (type == Float.class || type == float.class) {
            return 0.0f;
        } else if (type == Boolean.class || type == boolean.class) {
            return false;
        } else if (type == List.class) {
            return new ArrayList<>();
        } else if (type == JSONObject.class) {
            return new JSONObject();
        } else {
            return null;
        }
    }

    /**
     * 获取测试用例文件路径
     *
     * @param className  类名
     * @param methodName 方法名
     * @return 测试用例文件路径
     */
    private String getTestCaseFilePath(String className, String methodName) {
        String packagePath = className.replace('.', '/');
        return TEST_CASE_DIR + packagePath + "/" + methodName + ".json";
    }

    /**
     * 写入测试用例文件
     *
     * @param testCase 测试用例
     * @param filePath 文件路径
     */
    private void writeTestCaseFile(TestCase testCase, String filePath) {
        try {
            File file = new File(filePath);
            File parentDir = file.getParentFile();
            if (!parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                String json = JSON.toJSONString(testCase, JSONWriter.Feature.PrettyFormat);
                writer.write(json);
                log.info("生成测试用例文件: {}", filePath);
            }
        } catch (Exception e) {
            log.error("写入测试用例文件失败: {}", filePath, e);
        }
    }
}