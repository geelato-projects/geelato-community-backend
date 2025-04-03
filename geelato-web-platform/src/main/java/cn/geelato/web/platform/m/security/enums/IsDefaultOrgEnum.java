package cn.geelato.web.platform.m.security.enums;

import lombok.Getter;

/**
 * @author diabl
 */

@Getter
public enum IsDefaultOrgEnum {
    IS("是", 1),
    NO("否", 0);

    private final String label;
    private final int value;

    IsDefaultOrgEnum(String label, int value) {
        this.label = label;
        this.value = value;
    }
}
