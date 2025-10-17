package cn.geelato.test.controller;

import cn.geelato.test.service.DataSourceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 数据源测试控制器
 * 提供API接口用于获取数据源信息和测试数据源连接
 */
@Slf4j
@RestController
@RequestMapping("/api/test/datasource")
public class DataSourceTestController {

    @Autowired
    private DataSourceService dataSourceService;

    /**
     * 获取所有数据源信息
     *
     * @return 数据源信息列表
     */
    @GetMapping("/list")
    public List<Map<String, Object>> getAllDataSources() {
        log.info("获取所有数据源信息");
        return dataSourceService.getAllDataSources();
    }

    /**
     * 测试指定数据源的连接
     *
     * @param dataSourceName 数据源名称
     * @return 测试结果
     */
    @GetMapping("/test")
    public Map<String, Object> testDataSourceConnection(@RequestParam String dataSourceName) {
        log.info("测试数据源连接: {}", dataSourceName);
        return dataSourceService.testDataSourceConnection(dataSourceName);
    }

    /**
     * 测试所有数据源的连接
     *
     * @return 测试结果列表
     */
    @GetMapping("/test/all")
    public List<Map<String, Object>> testAllDataSourceConnections() {
        log.info("测试所有数据源连接");
        return dataSourceService.testAllDataSourceConnections();
    }
}