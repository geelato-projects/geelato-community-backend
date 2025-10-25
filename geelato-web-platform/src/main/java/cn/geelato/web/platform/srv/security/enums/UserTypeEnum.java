package cn.geelato.web.platform.srv.security.enums;

import lombok.Getter;

@Getter
public enum UserTypeEnum {
    EMPLOYEE("员工账号", 0, false),
    SYSTEM("系统账号", 1, false),
    EXTERNAL("企业外人员", 2, false),
    ADMINISTRATOR("租户管理员", 999, true);

    private final String label;// 选项内容
    private final int value;// 选项值
    private final boolean disabled; // 是否禁用

    UserTypeEnum(String label, int value, boolean disabled) {
        this.label = label;
        this.value = value;
        this.disabled = disabled;
    }
}
