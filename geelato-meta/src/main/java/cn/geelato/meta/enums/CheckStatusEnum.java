package cn.geelato.meta.enums;

import lombok.Getter;

/**
 * 签入签出状态枚举
 * @author itechgee@126.com
 */
@Getter
public enum CheckStatusEnum {
    UNCHECKED("未签出", "unchecked"),
    CHECKED_OUT("已签出", "checkedOut");

    private final String label;
    private final String value;

    CheckStatusEnum(String label, String value) {
        this.label = label;
        this.value = value;
    }
}