package cn.geelato.test.service;

import cn.geelato.test.model.TestResult;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 测试结果存储服务
 * 负责将测试结果保存到指定文件夹中
 */
@Slf4j
@Service
public class TestResultStorage {

    @Value("${geelato.test.result-path:./test-results}")
    private String resultPath;

    /**
     * 保存测试结果
     *
     * @param result 测试结果
     * @return 保存的文件路径
     */
    public String saveTestResult(TestResult result) {
        try {
            // 创建结果目录
            Path dirPath = Paths.get(resultPath);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            // 生成文件名
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String fileName = String.format("%s_%s_%s.json", result.getClassName().replaceAll("\\.", "_"), 
                    result.getMethodName(), timestamp);
            Path filePath = dirPath.resolve(fileName);

            // 写入文件
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(JSON.toJSONString(result, String.valueOf(true)));
            }

            log.info("测试结果已保存: {}", filePath);
            return filePath.toString();
        } catch (IOException e) {
            log.error("保存测试结果失败", e);
            return null;
        }
    }

    /**
     * 获取指定类和方法的最新测试结果
     *
     * @param className  类名
     * @param methodName 方法名
     * @return 测试结果
     */
    public TestResult getLatestTestResult(String className, String methodName) {
        try {
            Path dirPath = Paths.get(resultPath);
            if (!Files.exists(dirPath)) {
                return null;
            }

            String prefix = className.replaceAll("\\.", "_") + "_" + methodName;
            File dir = dirPath.toFile();
            File[] files = dir.listFiles((d, name) -> name.startsWith(prefix) && name.endsWith(".json"));

            if (files == null || files.length == 0) {
                return null;
            }

            // 找到最新的文件
            File latestFile = files[0];
            for (File file : files) {
                if (file.lastModified() > latestFile.lastModified()) {
                    latestFile = file;
                }
            }

            // 读取文件内容
            String content = new String(Files.readAllBytes(latestFile.toPath()));
            return JSON.parseObject(content, TestResult.class);
        } catch (Exception e) {
            log.error("获取最新测试结果失败: {}.{}", className, methodName, e);
            return null;
        }
    }
}