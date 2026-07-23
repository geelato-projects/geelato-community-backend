package cn.geelato.ide.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * dry-run 结果（透传 worker 的 WorkerExecuteResult）。
 *
 * @author geelato
 */
@Data
public class IdeDryRunResult {
    private boolean success;
    private Object returnValue;
    private List<String> logs = new ArrayList<>();
    private String errorType;
    private String errorMessage;
    /** 失败时的源码位置（"line 3, col 12 in <eval>"），无则 null */
    private String errorLocation;
    /** 失败时的 polyglot 调用栈（每帧一行） */
    private List<String> stackTrace = new ArrayList<>();
    private long durationMs;
    private boolean rolledBack;
    /** 来源：worker / unavailable（worker 不可达时） */
    private String source;
}
