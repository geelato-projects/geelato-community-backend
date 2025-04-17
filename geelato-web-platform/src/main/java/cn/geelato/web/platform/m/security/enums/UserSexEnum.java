package cn.geelato.web.platform.m.security.enums;

import lombok.Getter;

@Getter
public enum UserSexEnum {
    MALE("男", 1),
    FEMALE("女", 0);

    private final String label;// 选项内容
    private final int value;// 选项值

    UserSexEnum(String label, int value) {
        this.label = label;
        this.value = value;
    }
}
