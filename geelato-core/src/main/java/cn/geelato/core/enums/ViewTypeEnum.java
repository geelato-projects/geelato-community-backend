package cn.geelato.core.enums;

import lombok.Getter;

/**
 * @author diabl
 * @description: 表格视图，类型枚举
 */
@Getter
public enum ViewTypeEnum {
    DEFAULT("default", "默认视图"),
    CUSTOM("custom", "自定义视图");

    private final String code;
    private final String name;

    ViewTypeEnum(String code, String name) {
        this.code = code;
        this.name = name;
    }
}
