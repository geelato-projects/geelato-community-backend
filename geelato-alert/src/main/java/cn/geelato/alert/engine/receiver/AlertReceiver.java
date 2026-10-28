package cn.geelato.alert.engine.receiver;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 求解出的告警接收人。 */
@Getter
@AllArgsConstructor
public class AlertReceiver {

    private final String id;
    private final String name;
    private final String orgId;
}
