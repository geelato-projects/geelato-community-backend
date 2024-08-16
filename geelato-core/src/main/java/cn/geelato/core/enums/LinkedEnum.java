package cn.geelato.core.enums;

/**
 * @author diabl
 * @description: table、table_column 是否连接
 */
public enum LinkedEnum {
    IS("已连接", 1), NO("未连接", 0);

    private final String label;// 选项内容
    private final Integer value;// 选项值

    LinkedEnum(String label, Integer value) {
        this.label = label;
        this.value = value;
    }

    public String getLabel() {
        return label;
    }

    public Integer getValue() {
        return value;
    }
}
