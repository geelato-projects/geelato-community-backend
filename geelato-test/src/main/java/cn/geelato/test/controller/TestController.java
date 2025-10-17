package cn.geelato.test.controller;

import cn.geelato.test.executor.TestExecutor;
import cn.geelato.test.model.TestApiInfo;
import cn.geelato.test.model.TestResult;
import cn.geelato.test.scanner.TestScanner;
import cn.geelato.test.service.TestApiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 测试控制器
 * 提供API接口用于触发测试
 */
@Slf4j
@RestController
@RequestMapping("/api/test")
public class TestController {

    @Autowired
    private TestExecutor testExecutor;

    @Autowired
    private TestScanner testScanner;
    
    @Autowired
    private TestApiService testApiService;

    /**
     * 执行指定类和方法的测试
     *
     * @param className  类名
     * @param methodName 方法名
     * @return 测试结果
     */
    @GetMapping("/execute")
    public TestResult executeTest(@RequestParam String className, @RequestParam String methodName) {
        log.info("执行测试: {}.{}", className, methodName);
        return testExecutor.executeTest(className, methodName);
    }

    /**
     * 执行指定类的所有测试
     *
     * @param className 类名
     * @return 测试结果列表
     */
    @GetMapping("/execute/class")
    public List<TestResult> executeClassTests(@RequestParam String className) {
        log.info("执行类测试: {}", className);
        return testScanner.scanAndExecuteClassTests(className);
    }

    /**
     * 执行指定包的所有测试
     *
     * @param packageName 包名
     * @return 测试结果列表
     */
    @GetMapping("/execute/package")
    public List<TestResult> executePackageTests(@RequestParam String packageName) {
        log.info("执行包测试: {}", packageName);
        return testScanner.scanAndExecutePackageTests(packageName);
    }

    /**
     * 执行所有测试
     *
     * @return 测试结果列表
     */
    @GetMapping("/execute/all")
    public List<TestResult> executeAllTests() {
        log.info("执行所有测试");
        return testScanner.scanAndExecuteAllTests();
    }
    
    /**
     * 获取所有测试接口清单
     *
     * @return 测试接口清单列表
     */
    @GetMapping("/apis")
    public List<TestApiInfo> getAllTestApis() {
        log.info("获取所有测试接口清单");
        return testApiService.getAllTestApis();
    }
    
    /**
     * 获取指定类的测试接口清单
     *
     * @param className 类名
     * @return 测试接口清单列表
     */
    @GetMapping("/apis/class")
    public List<TestApiInfo> getTestApisByClass(@RequestParam String className) {
        log.info("获取类测试接口清单: {}", className);
        return testApiService.getTestApisByClass(className);
    }
}