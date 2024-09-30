package cn.geelato.web.platform.m.base.enums;

import lombok.Getter;

@Getter
public enum ScheduleLogStatusEnum {
    EXECUTING("执行中", "execute"),
    SUCCESS("运行成功", "success"),
    FAIL("运行失败", "fail");

    private final String label;// 选项内容
    private final String value;// 选项值

    ScheduleLogStatusEnum(String label, String value) {
        this.label = label;
        this.value = value;
    }
}
