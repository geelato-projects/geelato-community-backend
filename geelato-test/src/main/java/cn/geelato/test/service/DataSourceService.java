package cn.geelato.test.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 数据源服务
 * 用于获取系统中配置的数据源信息和测试数据源连接
 */
@Slf4j
@Service
public class DataSourceService {

    @Autowired
    private ConfigurableEnvironment environment;

    @Autowired(required = false)
    private Map<String, DataSource> dataSourceMap = new ConcurrentHashMap<>();

    /**
     * 获取所有数据源信息
     *
     * @return 数据源信息列表
     */
    public List<Map<String, Object>> getAllDataSources() {
        List<Map<String, Object>> result = new ArrayList<>();
        
        // 从环境变量中获取数据源配置信息
        Map<String, Object> datasourceProperties = getPropertiesByPrefix("spring.datasource");
        
        // 从数据源Map中获取已注册的数据源
        for (Map.Entry<String, DataSource> entry : dataSourceMap.entrySet()) {
            String name = entry.getKey();
            DataSource dataSource = entry.getValue();
            
            Map<String, Object> dsInfo = new HashMap<>();
            dsInfo.put("name", name);
            
            // 尝试获取数据源URL
            String urlKey = "spring.datasource." + name.replace("DataSource", "").toLowerCase() + ".url";
            String jdbcUrlKey = "spring.datasource." + name.replace("DataSource", "").toLowerCase() + ".jdbc-url";
            
            String url = (String) datasourceProperties.get(urlKey);
            if (url == null) {
                url = (String) datasourceProperties.get(jdbcUrlKey);
            }
            
            dsInfo.put("jdbcUrl", url);
            dsInfo.put("type", dataSource.getClass().getName());
            
            result.add(dsInfo);
        }
        
        return result;
    }
    
    /**
     * 测试指定数据源的连接
     *
     * @param dataSourceName 数据源名称
     * @return 测试结果
     */
    public Map<String, Object> testDataSourceConnection(String dataSourceName) {
        Map<String, Object> result = new HashMap<>();
        result.put("name", dataSourceName);
        result.put("success", false);
        
        DataSource dataSource = dataSourceMap.get(dataSourceName);
        if (dataSource == null) {
            result.put("message", "数据源不存在: " + dataSourceName);
            return result;
        }
        
        long startTime = System.currentTimeMillis();
        try (Connection connection = dataSource.getConnection()) {
            boolean valid = connection.isValid(5); // 5秒超时
            long endTime = System.currentTimeMillis();
            
            result.put("success", valid);
            result.put("message", valid ? "连接成功" : "连接无效");
            result.put("responseTime", (endTime - startTime) + "ms");
            
            // 添加数据库信息
            if (valid) {
                result.put("databaseProductName", connection.getMetaData().getDatabaseProductName());
                result.put("databaseProductVersion", connection.getMetaData().getDatabaseProductVersion());
            }
        } catch (SQLException e) {
            long endTime = System.currentTimeMillis();
            result.put("message", "连接失败: " + e.getMessage());
            result.put("responseTime", (endTime - startTime) + "ms");
            log.error("测试数据源连接失败: {}", dataSourceName, e);
        }
        
        return result;
    }
    
    /**
     * 测试所有数据源的连接
     *
     * @return 测试结果列表
     */
    public List<Map<String, Object>> testAllDataSourceConnections() {
        return dataSourceMap.keySet().stream()
                .map(this::testDataSourceConnection)
                .collect(Collectors.toList());
    }
    
    /**
     * 根据前缀获取配置属性
     *
     * @param prefix 前缀
     * @return 配置属性Map
     */
    private Map<String, Object> getPropertiesByPrefix(String prefix) {
        Map<String, Object> properties = new TreeMap<>();
        
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if (propertySource instanceof EnumerablePropertySource<?> enumerablePropertySource) {
                for (String name : enumerablePropertySource.getPropertyNames()) {
                    if (name.startsWith(prefix)) {
                        try {
                            Object value = environment.getProperty(name);
                            // 过滤掉敏感信息
                            if (!isSensitiveProperty(name)) {
                                properties.put(name, value);
                            } else {
                                properties.put(name, "******");
                            }
                        } catch (Exception e) {
                            properties.put(name, "未配置");
                        }
                    }
                }
            }
        }
        
        return properties;
    }
    
    /**
     * 判断是否为敏感属性
     *
     * @param propertyName 属性名
     * @return 是否敏感
     */
    private boolean isSensitiveProperty(String propertyName) {
        String lowerName = propertyName.toLowerCase();
        return lowerName.contains("password") || 
               lowerName.contains("secret") || 
               lowerName.contains("key") || 
               lowerName.contains("token") ||
               lowerName.contains("credential");
    }
}