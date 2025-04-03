package cn.geelato.web.platform.m.security.enums;

import lombok.Getter;

/**
 * @author diabl
 */
@Getter
public enum RoleTypeEnum {
    PLATFORM("平台级角色", "platform"),
    APP("应用级角色", "app");

    private final String label;// 选项内容
    private final String value;// 选项值

    RoleTypeEnum(String label, String value) {
        this.label = label;
        this.value = value;
    }
}
