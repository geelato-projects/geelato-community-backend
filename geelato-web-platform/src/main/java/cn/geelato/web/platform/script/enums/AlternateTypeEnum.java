package cn.geelato.web.platform.script.enums;

import org.apache.logging.log4j.util.Strings;

/**
 * @author diabl
 */
public enum AlternateTypeEnum {
    REQUEST("Request", "request"), RESPONSE("Response", "response");

    private final String label;// 选项内容
    private final String value;// 选项值

    AlternateTypeEnum(String label, String value) {
        this.label = label;
        this.value = value;
    }

    public String getLabel() {
        return label;
    }

    public String getValue() {
        return value;
    }

    public static String getLabel(String value) {
        if (Strings.isNotBlank(value)) {
            for (AlternateTypeEnum enums : AlternateTypeEnum.values()) {
                if (enums.getValue().equals(value)) {
                    return enums.getLabel();
                }
            }
        }
        return null;
    }
}