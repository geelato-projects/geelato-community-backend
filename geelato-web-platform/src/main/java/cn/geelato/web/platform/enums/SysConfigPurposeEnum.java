package cn.geelato.web.platform.enums;

import lombok.Getter;
import org.apache.logging.log4j.util.Strings;

/**
 * @author diabl
 */
@Getter
public enum SysConfigPurposeEnum {
    ALL("所有", "all"),
    WEBAPP("前端", "webapp"),
    ENDPOINT("后端", "endpoint"),
    WORKFLOW("工作流", "workflow"),
    SCHEDULE("调度", " schedule");

    private final String label;//选项内容
    private final String value;//选项值

    SysConfigPurposeEnum(String label, String value) {
        this.label = label;
        this.value = value;
    }

    public static String getLabel(String value) {
        if (Strings.isNotBlank(value)) {
            for (SysConfigPurposeEnum enums : SysConfigPurposeEnum.values()) {
                if (enums.getValue().equals(value)) {
                    return enums.getLabel();
                }
            }
        }
        return null;
    }
}