package cn.geelato.script.core;

import lombok.Getter;

/**
 * 单次 kit 调用踪迹：调试与审计共用，随 ScriptResult.trace 返回（上限 100 条）。
 */
@Getter
public class TraceEntry {

    /** 摘要上限只防极端滥用（脚本拼超长 SQL/URL），真实语句全文展示。 */
    private static final int MAX_SUMMARY = 8000;

    private final String ns;
    private final String method;
    private final String summary;
    private final long rows;
    private final long costMs;
    private final long at;

    private TraceEntry(String ns, String method, String summary, long rows, long costMs, long at) {
        this.ns = ns;
        this.method = method;
        this.summary = summarize(summary);
        this.rows = rows;
        this.costMs = costMs;
        this.at = at;
    }

    public static TraceEntry of(String ns, String method, String summary, long rows, long costMs) {
        return new TraceEntry(ns, method, summary, rows, costMs, System.currentTimeMillis());
    }

    /** 摘要单行化（换行压空格）+ 超长截断标注。 */
    public static String summarize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String one = raw.replace("\r\n", "\n").replace("\r", "\n").replace("\n", " ").trim();
        return one.length() <= MAX_SUMMARY ? one
                : one.substring(0, MAX_SUMMARY) + "...(已截断,总长" + one.length() + ")";
    }
}
