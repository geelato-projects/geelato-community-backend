package cn.geelato.exception.engine.core;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * 管道执行结果（含运行期契约兜底）：SCAN/RECHECK 消费 validHits，TRIAL 仅回显。
 */
@Getter
@Setter
public class PipelineOutcome {

    /** SUCCESS / STEP_FAILED / PIPELINE_TIMEOUT / CONTRACT_FATAL。 */
    private String status;
    /** 契约兜底后的有效命中。 */
    private List<Map<String, Object>> validHits;
    /** 契约兜底丢弃明细。 */
    private List<Map<String, Object>> violations;
    private List<Map<String, Object>> steps;
    private long candidateCount;
    private long costMs;
    private String engineVersion;
    private String deprecationWarning;
    /** 失败定位（steps_json 的 error 与 scan_log.error 用）。 */
    private String error;
}
