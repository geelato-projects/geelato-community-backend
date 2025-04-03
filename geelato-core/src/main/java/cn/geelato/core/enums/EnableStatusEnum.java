package cn.geelato.core.enums;

import lombok.Getter;

/**
 * @author diabl
 */
@Getter
public enum EnableStatusEnum {
    ENABLED("启用", 1),
    DISABLED("禁用", 0);

    private final String label;
    private final int value;

    EnableStatusEnum(String label, int value) {
        this.label = label;
        this.value = value;
    }
}
