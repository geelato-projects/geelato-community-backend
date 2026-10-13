package cn.geelato.script.core;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ScriptResult {

    /** SUCCESS / ERROR / TIMEOUT。 */
    private String status;
    /** 脚本返回值（unwrap 为 Java 类型：List/Map/String/Number/Boolean/null）。 */
    private Object value;
    private List<String> console;
    private long costMs;
    /** 本次执行 SQL 查询行数计数（$gl.dao kit 口径，供消费方累计候选量）。 */
    private long rowCount;
    /** 实际执行的引擎版本。 */
    private String engineVersion;
    /** 版本为 DEPRECATED 时的迁移提示，非空即警告。 */
    private String deprecationWarning;
    /** 执行 ID（$env.execId 同源）。 */
    private String execId;
    /** kit 调用踪迹（调试与审计，上限 100 条）。 */
    private List<TraceEntry> trace;
    private ScriptError error;

    public static ScriptResult success(Object value, List<String> console, long costMs, String version) {
        ScriptResult r = new ScriptResult();
        r.setStatus("SUCCESS");
        r.setValue(value);
        r.setConsole(console);
        r.setCostMs(costMs);
        r.setEngineVersion(version);
        return r;
    }

    public static ScriptResult failure(String status, ScriptError error, List<String> console, long costMs, String version) {
        ScriptResult r = new ScriptResult();
        r.setStatus(status);
        r.setError(error);
        r.setConsole(console);
        r.setCostMs(costMs);
        r.setEngineVersion(version);
        return r;
    }
}
