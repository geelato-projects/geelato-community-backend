package cn.geelato.exception.engine.lifecycle;

import lombok.Getter;

/**
 * 异常实例生命周期事件（Spring ApplicationEvent 出口，零耦合消费者预留——告警中心等按需监听）：
 * EXC_CREATED / EXC_CLAIMED / EXC_RESOLVED / EXC_IGNORED / EXC_ESCALATED / EXC_RECOVERED。
 */
@Getter
public class ExceptionLifecycleEvent extends org.springframework.context.ApplicationEvent {

    private final String eventType;
    private final String exceptionId;
    private final String ruleCode;
    private final String sourceKey;
    private final String participantId;

    public ExceptionLifecycleEvent(Object source, String eventType, String exceptionId,
                                   String ruleCode, String sourceKey, String participantId) {
        super(source);
        this.eventType = eventType;
        this.exceptionId = exceptionId;
        this.ruleCode = ruleCode;
        this.sourceKey = sourceKey;
        this.participantId = participantId;
    }
}
