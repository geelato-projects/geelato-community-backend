package cn.geelato.web.platform.plugin.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 插件日志工具类。
 * <p>P2-A：改为走 SLF4J（logback），logger 名 {@code plugin.<pluginId>}，
 * 接入主程序统一日志收集与滚动策略。废弃自写 {@code plugins/logs/<id>.log} 文件。</p>
 *
 * <p>静态 API 保持不变，避免改动既有调用点（{@code PluginManagerController}）。</p>
 *
 * @author geelato
 */
public class PluginLogUtil {

    /** 缓存 plugin.<id> logger。 */
    private static final ConcurrentHashMap<String, Logger> LOGGERS = new ConcurrentHashMap<>();

    /**
     * 记录插件日志（info 级别，走 logback）。
     *
     * @param pluginId 插件ID
     * @param message  日志消息
     */
    public static void log(String pluginId, String message) {
        logger(pluginId).info(message);
    }

    /**
     * 记录插件日志（指定级别）。
     */
    public static void log(String pluginId, String level, String message) {
        Logger logger = logger(pluginId);
        if (level == null) {
            logger.info(message);
        } else switch (level.toLowerCase()) {
            case "error" -> logger.error(message);
            case "warn" -> logger.warn(message);
            case "debug" -> logger.debug(message);
            case "trace" -> logger.trace(message);
            default -> logger.info(message);
        }
    }

    /**
     * 清除插件日志。
     * <p>logback 由其自身滚动策略管理，无需手动清除；保留方法签名兼容旧调用，恒返回 true。</p>
     *
     * @param pluginId 插件ID
     * @return 恒为 true
     * @deprecated 日志已由 logback 管理，该方法无实际作用
     */
    @Deprecated
    public static boolean clearLog(String pluginId) {
        return true;
    }

    private static Logger logger(String pluginId) {
        String id = (pluginId == null || pluginId.isBlank()) ? "default" : pluginId;
        return LOGGERS.computeIfAbsent(id, k -> LoggerFactory.getLogger("plugin." + k));
    }
}
