package cn.geelato.plugin.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 插件日志工具类
 */
public class PluginLogUtil {

    private static final String PLUGINS_LOG_DIR = "plugins/logs";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /**
     * 记录插件日志
     * @param pluginId 插件ID
     * @param message 日志消息
     */
    public static void log(String pluginId, String message) {
        try {
            // 确保日志目录存在
            Path logDir = Paths.get(PLUGINS_LOG_DIR);
            if (!Files.exists(logDir)) {
                Files.createDirectories(logDir);
            }

            // 构建日志文件路径
            Path logFilePath = Paths.get(PLUGINS_LOG_DIR, pluginId + ".log");
            File logFile = logFilePath.toFile();

            // 创建日志文件（如果不存在）
            if (!logFile.exists()) {
                logFile.createNewFile();
            }

            // 写入日志
            try (FileWriter writer = new FileWriter(logFile, true)) {
                String timestamp = DATE_FORMAT.format(new Date());
                writer.write(String.format("[%s] %s\n", timestamp, message));
                writer.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 清除插件日志
     * @param pluginId 插件ID
     * @return 是否成功清除
     */
    public static boolean clearLog(String pluginId) {
        try {
            Path logFilePath = Paths.get(PLUGINS_LOG_DIR, pluginId + ".log");
            File logFile = logFilePath.toFile();
            
            if (logFile.exists()) {
                return logFile.delete();
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}