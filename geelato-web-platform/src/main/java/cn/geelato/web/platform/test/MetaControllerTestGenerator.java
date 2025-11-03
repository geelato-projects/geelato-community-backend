package cn.geelato.web.platform.test;

import cn.geelato.test.generator.TestCaseGenerator;
import cn.geelato.web.platform.srv.meta.MetaController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MetaController测试用例生成器
 * 用于自动生成MetaController的测试用例
 */
@Slf4j
//@Component
public class MetaControllerTestGenerator implements CommandLineRunner {

    @Autowired
    private TestCaseGenerator testCaseGenerator;

    @Override
    public void run(String... args) throws Exception {
        generateTestCases();
    }

    /**
     * 生成MetaController的测试用例
     */
    public void generateTestCases() {
        log.info("开始生成MetaController测试用例");
        List<String> files = testCaseGenerator.generateTestCases(MetaController.class);
        log.info("生成MetaController测试用例完成，共生成{}个测试用例文件", files.size());
        for (String file : files) {
            log.info("生成测试用例文件: {}", file);
        }
    }
}