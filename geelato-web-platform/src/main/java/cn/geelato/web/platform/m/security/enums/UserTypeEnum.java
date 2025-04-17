package cn.geelato.web.platform.m.security.enums;

import lombok.Getter;

@Getter
public enum UserTypeEnum {
    EMPLOYEE("员工账号", 0),
    SYSTEM("系统账号", 1),
    EXTERNAL("企业外人员", 2);

    private final String label;// 选项内容
    private final int value;// 选项值

    UserTypeEnum(String label, int value) {
        this.label = label;
        this.value = value;
    }
}
