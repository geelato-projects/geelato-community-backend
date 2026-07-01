package cn.geelato.web.platform.srv.security.enums;

import lombok.Getter;

@Getter
public enum UserSourceEnum {
    LOCAL_USER("本地用户", 0),
    SYSTEM_SYNC("系统同步", 1);

    private final String label;// 选项内容
    private final int value;// 选项值

    UserSourceEnum(String label, int value) {
        this.label = label;
        this.value = value;
    }
}
