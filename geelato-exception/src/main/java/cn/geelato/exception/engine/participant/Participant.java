package cn.geelato.exception.engine.participant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 求解出的参与者（处理人/知会人）。 */
@Getter
@AllArgsConstructor
public class Participant {

    private final String id;
    private final String name;
    private final String orgId;
}
