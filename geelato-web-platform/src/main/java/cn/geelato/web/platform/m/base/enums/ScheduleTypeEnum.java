package cn.geelato.web.platform.m.base.enums;

import lombok.Getter;
import org.apache.logging.log4j.util.Strings;

@Getter
public enum ScheduleTypeEnum {
    SQL("基于sqlKey调用", "sql"),
    GENERAL("基于通用方法调用", "general"),
    JAVA("基于java类注册的类进行调用", "java");

    private final String label;// 选项内容
    private final String value;// 选项值

    ScheduleTypeEnum(String label, String value) {
        this.label = label;
        this.value = value;
    }

    public static String getLabel(String value) {
        if (Strings.isNotBlank(value)) {
            for (ScheduleTypeEnum enums : ScheduleTypeEnum.values()) {
                if (enums.getValue().equals(value)) {
                    return enums.getLabel();
                }
            }
        }
        return null;
    }
}
