package cn.geelato.exception.engine.hit;

import lombok.Getter;
import lombok.Setter;

/** 一轮命中提交报告（scan_log 回写计数）。 */
@Getter
@Setter
public class SubmitReport {

    private int instanceCreated;
    private int instanceRenewed;
    private int suppressedCount;
    private int overflowDropped;
    private int cooldownSkipped;
    private int noHandlerSkipped;
}
