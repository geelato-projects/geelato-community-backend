package cn.geelato.web.platform.m.base.enums;

import lombok.Getter;

@Getter
public enum ScheduleLogStatusEnum {
    FAIL("失败", 0),
    SUCCESS("成功", 1),
    EXECUTING("执行中", 2);

    private final String label;// 选项内容
    private final int value;// 选项值

    ScheduleLogStatusEnum(String label, int value) {
        this.label = label;
        this.value = value;
    }
}
