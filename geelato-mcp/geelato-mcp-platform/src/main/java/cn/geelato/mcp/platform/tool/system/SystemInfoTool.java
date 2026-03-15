package cn.geelato.mcp.platform.tool.system;

import cn.geelato.mcp.common.tool.BaseMcpTool;
import com.alibaba.fastjson2.JSON;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 系统信息工具
 * 提供系统运行状态和信息查询功能
 */
@Component
public class SystemInfoTool extends BaseMcpTool {

    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        response.put("data", null);
        return response;
    }

    private Map<String, Object> createSuccessResponse(Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "查询成功");
        response.put("data", data);
        return response;
    }

    public String getSystemInfo() {
        logToolExecution("getSystemInfo");
        try {
            Map<String, Object> info = new HashMap<>();
            
            // 应用信息
            info.put("appName", "Geelato MCP Platform");
            info.put("version", "1.0.0-SNAPSHOT");
            info.put("description", "Geelato 模型上下文协议服务平台");
            
            // Java 信息
            info.put("javaVersion", System.getProperty("java.version"));
            info.put("javaVendor", System.getProperty("java.vendor"));
            info.put("javaHome", System.getProperty("java.home"));
            
            // 操作系统信息
            info.put("osName", System.getProperty("os.name"));
            info.put("osVersion", System.getProperty("os.version"));
            info.put("osArch", System.getProperty("os.arch"));
            
            // 运行时信息
            RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
            long uptime = runtimeMXBean.getUptime();
            info.put("uptime", formatUptime(uptime));
            info.put("startTime", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(runtimeMXBean.getStartTime())));
            
            String jsonResult = JSON.toJSONString(createSuccessResponse(info));
            logToolResult("getSystemInfo", "返回系统基本信息");
            return jsonResult;
        } catch (Exception e) {
            logToolError("getSystemInfo", e);
            return JSON.toJSONString(createErrorResponse("查询失败: " + e.getMessage()));
        }
    }

    public String getMemoryInfo() {
        logToolExecution("getMemoryInfo");
        try {
            Map<String, Object> memory = new HashMap<>();
            
            Runtime runtime = Runtime.getRuntime();
            
            // 堆内存
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            long maxMemory = runtime.maxMemory();
            
            memory.put("totalMemoryMB", totalMemory / 1024 / 1024);
            memory.put("freeMemoryMB", freeMemory / 1024 / 1024);
            memory.put("usedMemoryMB", usedMemory / 1024 / 1024);
            memory.put("maxMemoryMB", maxMemory / 1024 / 1024);
            memory.put("usedPercentage", String.format("%.2f%%", (double) usedMemory / maxMemory * 100));
            
            String jsonResult = JSON.toJSONString(createSuccessResponse(memory));
            logToolResult("getMemoryInfo", "返回内存使用信息");
            return jsonResult;
        } catch (Exception e) {
            logToolError("getMemoryInfo", e);
            return JSON.toJSONString(createErrorResponse("查询失败: " + e.getMessage()));
        }
    }

    public String getCpuInfo() {
        logToolExecution("getCpuInfo");
        try {
            Map<String, Object> cpu = new HashMap<>();
            
            cpu.put("availableProcessors", Runtime.getRuntime().availableProcessors());
            
            // 获取 CPU 负载（如果支持）
            double systemLoad = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
            cpu.put("systemLoadAverage", systemLoad >= 0 ? String.format("%.2f", systemLoad) : "N/A");
            
            String jsonResult = JSON.toJSONString(createSuccessResponse(cpu));
            logToolResult("getCpuInfo", "返回 CPU 信息");
            return jsonResult;
        } catch (Exception e) {
            logToolError("getCpuInfo", e);
            return JSON.toJSONString(createErrorResponse("查询失败: " + e.getMessage()));
        }
    }

    public String getEnvironmentVariables() {
        logToolExecution("getEnvironmentVariables");
        try {
            Map<String, String> env = System.getenv();
            Map<String, String> filteredEnv = new HashMap<>();
            
            // 过滤掉敏感信息
            for (Map.Entry<String, String> entry : env.entrySet()) {
                String key = entry.getKey();
                if (!key.toLowerCase().contains("password") && 
                    !key.toLowerCase().contains("secret") && 
                    !key.toLowerCase().contains("key") &&
                    !key.toLowerCase().contains("token")) {
                    filteredEnv.put(key, entry.getValue());
                }
            }
            
            String jsonResult = JSON.toJSONString(createSuccessResponse(filteredEnv));
            logToolResult("getEnvironmentVariables", "返回 " + filteredEnv.size() + " 个环境变量");
            return jsonResult;
        } catch (Exception e) {
            logToolError("getEnvironmentVariables", e);
            return JSON.toJSONString(createErrorResponse("查询失败: " + e.getMessage()));
        }
    }

    public String getSystemProperties() {
        logToolExecution("getSystemProperties");
        try {
            Properties props = System.getProperties();
            Map<String, String> filteredProps = new HashMap<>();
            
            // 过滤掉敏感信息
            for (String key : props.stringPropertyNames()) {
                if (!key.toLowerCase().contains("password") && 
                    !key.toLowerCase().contains("secret") && 
                    !key.toLowerCase().contains("key") &&
                    !key.toLowerCase().contains("token")) {
                    filteredProps.put(key, props.getProperty(key));
                }
            }
            
            String jsonResult = JSON.toJSONString(createSuccessResponse(filteredProps));
            logToolResult("getSystemProperties", "返回 " + filteredProps.size() + " 个系统属性");
            return jsonResult;
        } catch (Exception e) {
            logToolError("getSystemProperties", e);
            return JSON.toJSONString(createErrorResponse("查询失败: " + e.getMessage()));
        }
    }

    @Tool(description = "系统：获取系统基础信息")
    public String system_get_info() {
        return getSystemInfo();
    }

    @Tool(description = "系统：获取内存信息")
    public String system_get_memory_info() {
        return getMemoryInfo();
    }

    @Tool(description = "系统：获取CPU信息")
    public String system_get_cpu_info() {
        return getCpuInfo();
    }

    @Tool(description = "系统：获取环境变量")
    public String system_get_env() {
        return getEnvironmentVariables();
    }

    @Tool(description = "系统：获取系统属性")
    public String system_get_props() {
        return getSystemProperties();
    }

    private String formatUptime(long uptime) {
        long days = uptime / (24 * 60 * 60 * 1000);
        long hours = (uptime % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000);
        long minutes = (uptime % (60 * 60 * 1000)) / (60 * 1000);
        long seconds = (uptime % (60 * 1000)) / 1000;
        
        if (days > 0) {
            return String.format("%d天%d小时%d分钟%d秒", days, hours, minutes, seconds);
        } else if (hours > 0) {
            return String.format("%d小时%d分钟%d秒", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%d分钟%d秒", minutes, seconds);
        } else {
            return String.format("%d秒", seconds);
        }
    }
}
