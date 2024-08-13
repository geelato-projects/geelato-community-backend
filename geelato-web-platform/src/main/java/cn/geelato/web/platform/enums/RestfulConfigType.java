package cn.geelato.web.platform.enums;

import cn.geelato.utils.StringUtils;

/**
 * @author diabl
 */
public enum RestfulConfigType {
    SQL("Structured Query Language", "sql"),
    JS("Javascript", "js");

    private final String label;// 选项内容
    private final String value;// 选项值

    RestfulConfigType(String label, String value) {
        this.label = label;
        this.value = value;
    }

    public static String getLabel(String value) {
        if (StringUtils.isNotBlank(value)) {
            for (RestfulConfigType enums : RestfulConfigType.values()) {
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