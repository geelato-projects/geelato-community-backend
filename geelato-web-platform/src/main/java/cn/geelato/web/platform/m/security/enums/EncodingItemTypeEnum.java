package cn.geelato.web.platform.m.security.enums;

import lombok.Getter;

/**
 * @author diabl
 */
@Getter
public enum EncodingItemTypeEnum {
    CONSTANT("固定字段", "constant"),
    VARIABLE("系统变量", "variable"),
    ARGUMENT("传递参数", "argument"),
    SERIAL("序列号(唯一)", "serial"),
    DATE("日期", "date");

    private final String label;// 选项内容
    private final String value;// 选项值

    EncodingItemTypeEnum(String label, String value) {
        this.label = label;
        this.value = value;
    }
}
