package cn.geelato.core.enums;

/**
 * @author diabl
 * @description: 字段是否需要加密
 * @date 2023/12/27 9:50
 */
public enum ColumnEncryptedEnum {
    TRUE("需要加密", true), FALSE("不需要加密", false);

    private final String label;// 选项内容
    private final Boolean value;// 选项值

    ColumnEncryptedEnum(String label, Boolean value) {
        this.label = label;
        this.value = value;
    }

    public String getLabel() {
        return label;
    }

    public Boolean getValue() {
        return value;
    }
}
