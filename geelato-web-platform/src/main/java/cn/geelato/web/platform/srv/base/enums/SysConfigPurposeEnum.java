package cn.geelato.web.platform.srv.base.enums;

import lombok.Getter;

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

    private final String label;// 选项内容
    private final String value;// 选项值

    SysConfigPurposeEnum(String label, String value) {
        this.label = label;
        this.value = value;
    }
}
