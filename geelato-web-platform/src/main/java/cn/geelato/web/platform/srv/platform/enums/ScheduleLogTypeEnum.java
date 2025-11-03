package cn.geelato.web.platform.srv.platform.enums;

import lombok.Getter;

@Getter
public enum ScheduleLogTypeEnum {
    START("开始", "start"),
    EXECUTE("执行中", "execute"),
    FINISH("结束", "finish");

    private final String label;// 选项内容
    private final String value;// 选项值

    ScheduleLogTypeEnum(String label, String value) {
        this.label = label;
        this.value = value;
    }
}
