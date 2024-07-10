package cn.geelato.web.platform.enums;

import org.apache.logging.log4j.util.Strings;

/**
 * @author diabl
 * @date 2023/8/2 10:56
 */
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

    public static String getLabel(String value) {
        if (Strings.isNotBlank(value)) {
            for (EncodingItemTypeEnum enums : EncodingItemTypeEnum.values()) {
                if (enums.getValue().equals(value)) {
                    return enums.getLabel();
                }
            }
        }
        return null;
    }

    public String getLabel() {
        return label;
    }

    public String getValue() {
        return value;
    }
}