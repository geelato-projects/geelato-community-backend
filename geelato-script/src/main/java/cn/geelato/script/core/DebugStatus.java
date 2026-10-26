package cn.geelato.script.core;

import lombok.Builder;
import lombok.Getter;

/**
 * 调试会话状态快照（{@link ScriptDebugService#status}）。
 */
@Getter
@Builder
public class DebugStatus {

    /**
     * 会话状态。
     * <ul>
     *   <li>{@code WAITING}：debug Context 已建、inspector 已监听，脚本阻塞等待 DevTools 连接/命中断点；</li>
     *   <li>{@code RUNNING}：已 resume，脚本执行中（kit 副作用仍在 dry-run 事务内）；</li>
     *   <li>{@code EXITED}：脚本执行结束（自然完成或异常），Context 待回收；</li>
     *   <li>{@code STOPPED}：经 {@link ScriptDebugService#stop} 主动终止并释放端口。</li>
     * </ul>
     */
    public enum State {
        WAITING, RUNNING, EXITED, STOPPED
    }

    private final String sessionId;
    private final State state;
    /** 存在且未回收时为 true。 */
    private final boolean alive;
    /** 存在脚本错误时为 true（详情见 error 文案）。 */
    private final boolean error;
    private final String errorMessage;
}
