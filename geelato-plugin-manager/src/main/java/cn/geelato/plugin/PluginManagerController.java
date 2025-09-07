package cn.geelato.plugin;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.plugin.util.PluginLogUtil;
import cn.geelato.web.common.annotation.ApiRestController;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginManager;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 插件管理控制器
 */
@ApiRestController("/pm")
public class PluginManagerController {
    
    @Autowired
    private PluginManager pluginManager;
    
    private static final String PLUGINS_LOG_DIR = "plugins/logs";
    
    /**
     * 获取所有插件列表
     * @return 插件列表信息
     */
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public ApiResult<?> list() {
        try {
            List<Map<String, Object>> pluginInfoList = new ArrayList<>();
            
            // 获取所有插件
            List<PluginWrapper> plugins = pluginManager.getPlugins();
            
            // 转换为前端需要的格式
            for (PluginWrapper plugin : plugins) {
                PluginDescriptor descriptor = plugin.getDescriptor();
                Map<String, Object> pluginInfo = new HashMap<>();
                
                pluginInfo.put("id", descriptor.getPluginId());
                pluginInfo.put("version", descriptor.getVersion());
                pluginInfo.put("description", descriptor.getPluginDescription());
                pluginInfo.put("provider", descriptor.getProvider());
                pluginInfo.put("dependencies", descriptor.getDependencies());
                pluginInfo.put("state", plugin.getPluginState().toString());
                pluginInfo.put("enabled", plugin.getPluginState() == PluginState.STARTED);
                
                pluginInfoList.add(pluginInfo);
            }
            
            return ApiResult.success(pluginInfoList);
        } catch (Exception e) {
            return ApiResult.fail("获取插件列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 切换插件状态（启用/禁用）
     * @param pluginId 插件ID
     * @param status 目标状态 ("enable" 或 "disable")
     * @return 操作结果
     */
    @RequestMapping(value = "/switchStatus", method = RequestMethod.GET)
    public ApiResult<?> switchStatus(
            @RequestParam String pluginId,
            @RequestParam String status) {
        try {
            PluginWrapper plugin = pluginManager.getPlugin(pluginId);
            if (plugin == null) {
                return ApiResult.fail("插件不存在: " + pluginId);
            }
            
            boolean success = false;
            String message;
            
            if ("enable".equalsIgnoreCase(status)) {
                // 启用插件
                if (plugin.getPluginState() == PluginState.STARTED) {
                    message = "插件已经处于启用状态";
                    success = true;
                } else {
                    PluginState newState = pluginManager.startPlugin(pluginId);
                    success = (newState == PluginState.STARTED);
                    message = success ? "插件启用成功" : "插件启用失败";
                    
                    // 记录日志
                    PluginLogUtil.log(pluginId, "插件已启用");
                }
            } else if ("disable".equalsIgnoreCase(status)) {
                // 禁用插件
                if (plugin.getPluginState() == PluginState.STOPPED) {
                    message = "插件已经处于禁用状态";
                    success = true;
                } else {
                    PluginState newState = pluginManager.stopPlugin(pluginId);
                    success = (newState == PluginState.STOPPED);
                    message = success ? "插件禁用成功" : "插件禁用失败";
                    
                    // 记录日志
                    PluginLogUtil.log(pluginId, "插件已禁用");
                }
            } else {
                message = "无效的状态参数，请使用 'enable' 或 'disable'";
            }
            
            if (success) {
                return ApiResult.success(message);
            } else {
                return ApiResult.fail(message);
            }
            
        } catch (Exception e) {
            return ApiResult.fail("切换插件状态失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取插件日志
     * @param pluginId 插件ID
     * @return 插件日志内容
     */
    @RequestMapping(value = "/log", method = RequestMethod.GET)
    public ApiResult<?> log(@RequestParam String pluginId) {
        try {
            // 检查插件是否存在
            PluginWrapper plugin = pluginManager.getPlugin(pluginId);
            if (plugin == null) {
                return ApiResult.fail("插件不存在: " + pluginId);
            }
            
            // 构建日志文件路径
            Path logFilePath = Paths.get(PLUGINS_LOG_DIR, pluginId + ".log");
            File logFile = logFilePath.toFile();
            
            // 检查日志文件是否存在
            if (!logFile.exists()) {
                return ApiResult.success("插件暂无日志记录");
            }
            
            // 读取日志文件内容
            List<String> logLines = Files.readAllLines(logFilePath);
            String logContent = String.join("\n", logLines);
            
            Map<String, Object> result = new HashMap<>();
            result.put("pluginId", pluginId);
            result.put("logContent", logContent);
            
            return ApiResult.success(result);
        } catch (IOException e) {
            return ApiResult.fail("读取插件日志失败: " + e.getMessage());
        } catch (Exception e) {
            return ApiResult.fail("获取插件日志失败: " + e.getMessage());
        }
    }
    
    /**
     * 清除插件日志
     * @param pluginId 插件ID
     * @return 操作结果
     */
    @RequestMapping(value = "/clearLog", method = RequestMethod.GET)
    public ApiResult<?> clearLog(@RequestParam String pluginId) {
        try {
            // 检查插件是否存在
            PluginWrapper plugin = pluginManager.getPlugin(pluginId);
            if (plugin == null) {
                return ApiResult.fail("插件不存在: " + pluginId);
            }
            
            boolean success = PluginLogUtil.clearLog(pluginId);
            if (success) {
                return ApiResult.success("插件日志已清除");
            } else {
                return ApiResult.fail("清除插件日志失败");
            }
        } catch (Exception e) {
            return ApiResult.fail("清除插件日志失败: " + e.getMessage());
        }
    }
}
