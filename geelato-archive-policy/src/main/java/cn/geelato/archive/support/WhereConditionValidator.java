package cn.geelato.archive.support;

import java.util.regex.Pattern;

/**
 * CUSTOM 策略 WHERE 条件的静态安全校验（纯逻辑，无 IO，可单测）。
 *
 * <p>安全模型：条件仅参与"选 id"，搬移与删除始终按 {@code id IN (...)} 精确执行——
 * 条件写错最多少搬数据，绝不会丢数据。本校验的目标是杜绝多语句与文件读写类注入，
 * 并在启用阶段拦截明显的语法错误（试跑由 Service 层执行）。</p>
 */
public final class WhereConditionValidator {

    /** 禁止分号：防多语句拼接 */
    private static final Pattern P_SEMICOLON = Pattern.compile(";");

    /** 禁止注释：-- 与 /* *\/ 与 # */
    private static final Pattern P_COMMENT = Pattern.compile("(--)|(\\/\\*)|(\\*/)|(^|\\s)#");

    /**
     * 禁止的关键词（词边界完整匹配，update_at/set_xxx 等带下划线列名不误伤——
     * Java 正则 \b 视下划线为词字符，update_at 中 update 后无边界）。
     * 覆盖：DML/DDL、多语句控制、文件与outfile类（含带下划线的 load_file/load_data
     * 这类文件读写函数，须整体匹配防绕过）、连接管理类。
     */
    private static final Pattern P_FORBIDDEN = Pattern.compile(
            "(?i)\\b(insert|update|delete|drop|alter|create|truncate|rename|grant|revoke|merge|replace|call|exec|execute|set|load|outfile|dumpfile|into|values|prepare|handler|lock|unlock|kill|shutdown|show|use|describe|explain|analyze|optimize|check|repair|information_schema|performance_schema|mysql|sys|load_file|loadfile|load_data|loadxml|sys_exec|extractvalue|updatexml)\\b");

    private WhereConditionValidator() {
    }

    /**
     * @return null 表示通过；否则为拒绝原因（用于 70001 错误信息）
     */
    public static String validate(String condition) {
        if (condition == null || condition.isBlank()) {
            return null;
        }
        String trimmed = condition.trim();
        if (P_SEMICOLON.matcher(trimmed).find()) {
            return "自定义条件不允许包含分号（防多语句执行）";
        }
        if (P_COMMENT.matcher(trimmed).find()) {
            return "自定义条件不允许包含注释（--、/* */、#）";
        }
        java.util.regex.Matcher m = P_FORBIDDEN.matcher(trimmed);
        if (m.find()) {
            return "自定义条件包含不允许的关键词：" + m.group(1);
        }
        if (!isQuoteBalanced(trimmed)) {
            return "自定义条件中单引号不配对";
        }
        return null;
    }

    private static boolean isQuoteBalanced(String s) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\'') {
                count++;
            }
        }
        return count % 2 == 0;
    }
}
