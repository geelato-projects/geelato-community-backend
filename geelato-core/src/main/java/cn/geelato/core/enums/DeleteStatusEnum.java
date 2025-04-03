package cn.geelato.core.enums;

import lombok.Getter;

/**
 * @author diabl
 */
@Getter
public enum DeleteStatusEnum {
    IS("已删除", 1),
    NO("未删除", 0);

    private final String label;
    private final int value;

    DeleteStatusEnum(String label, int value) {
        this.label = label;
        this.value = value;
    }
}
