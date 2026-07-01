package cn.geelato.web.platform.srv.security.enums;

import lombok.Getter;

/**
 * @author diabl
 */
@Getter
public enum EncodingSerialTypeEnum {
    ORDER("顺序", "order"),
    RANDOM("随机", "random");

    private final String label;// 选项内容
    private final String value;// 选项值

    EncodingSerialTypeEnum(String label, String value) {
        this.label = label;
        this.value = value;
    }
}
