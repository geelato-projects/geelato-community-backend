package cn.geelato.core.enums;

import lombok.Getter;

/**
 * @author diabl
 * @description: 字段是否需要加密
 */
@Getter
public enum ColumnEncryptedEnum {
    TRUE("需要加密", true),
    FALSE("不需要加密", false);

    private final String label;// 选项内容
    private final Boolean value;// 选项值

    ColumnEncryptedEnum(String label, Boolean value) {
        this.label = label;
        this.value = value;
    }
}
