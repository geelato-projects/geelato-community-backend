package cn.geelato.mqltest.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 单个测试场景的执行结果。
 */
@Getter
@Setter
public class MqlScenarioResult {
    /** 场景标识 */
    private String scenarioId;
    /** 场景名称 */
    private String scenarioName;
    /** 分组 */
    private String category;
    /** 是否通过 */
    private boolean passed;
    /** 生成的 SQL */
    private String sql;
    /** 是否真实执行 */
    private boolean executed;
    /** 期望行数 */
    private Integer expectedRowCount;
    /** 实际行数 */
    private Integer actualRowCount;
    /** 实际返回的首行（用于排查） */
    private Map<String, Object> actualFirstRow;
    /** 耗时（毫秒） */
    private long elapsedMs;
    /** 错误信息（失败时） */
    private String error;
    /** 详细校验结果 */
    private List<String> details = new ArrayList<>();

    public static MqlScenarioResult pass(MqlScenario scenario, long elapsedMs) {
        MqlScenarioResult r = base(scenario, elapsedMs);
        r.setPassed(true);
        return r;
    }

    public static MqlScenarioResult fail(MqlScenario scenario, long elapsedMs, String error) {
        MqlScenarioResult r = base(scenario, elapsedMs);
        r.setPassed(false);
        r.setError(error);
        return r;
    }

    public static MqlScenarioResult base(MqlScenario scenario, long elapsedMs) {
        MqlScenarioResult r = new MqlScenarioResult();
        r.setScenarioId(scenario.getId());
        r.setScenarioName(scenario.getName());
        r.setCategory(scenario.getCategory());
        r.setElapsedMs(elapsedMs);
        return r;
    }
}
