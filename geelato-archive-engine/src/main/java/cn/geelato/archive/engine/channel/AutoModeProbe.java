package cn.geelato.archive.engine.channel;

import cn.geelato.archive.enums.ExecutionModeEnum;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 执行模式探测（AUTO）：按连接 URL 判定源库与归档库是否同实例。
 * <p>纯逻辑、不连库：同实例（host:port 一致）→ SERVER（INSERT SELECT 服务端搬移，零 JVM）；
 * 跨实例 → LOCAL_FILE（运行时若 localInfile 被禁用自动降级 JDBC）。</p>
 */
public final class AutoModeProbe {

    /** jdbc:mysql://host:port/db 形式，允许 host:port 省略 port */
    private static final Pattern P_MYSQL_URL = Pattern.compile(
            "(?i)jdbc:mysql://([^/?;]+)");

    private AutoModeProbe() {
    }

    /**
     * @param sourceUrl 源库连接 URL（主库）
     * @param targetUrl 归档库连接 URL（connectId 空 = 主库自身 → 必然同实例）
     */
    public static ExecutionModeEnum probe(String sourceUrl, String targetUrl) {
        if (targetUrl == null || targetUrl.isBlank() || targetUrl.equals(sourceUrl)) {
            return ExecutionModeEnum.SERVER;
        }
        String s = hostPort(sourceUrl);
        String t = hostPort(targetUrl);
        if (s == null || t == null) {
            // 无法解析（非标准 URL），退保守路径
            return ExecutionModeEnum.JDBC;
        }
        return s.equalsIgnoreCase(t) ? ExecutionModeEnum.SERVER : ExecutionModeEnum.LOCAL_FILE;
    }

    /** 提取 host[:port]；无法解析返回 null */
    public static String hostPort(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        Matcher m = P_MYSQL_URL.matcher(url);
        if (!m.find()) {
            return null;
        }
        return m.group(1).toLowerCase();
    }
}
