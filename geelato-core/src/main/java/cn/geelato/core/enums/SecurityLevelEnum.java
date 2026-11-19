package cn.geelato.core.enums;

import lombok.Getter;

/**
 * 系统密级，部署级防护能力分级。
 * <p>
 * 码值：0 开发级、1 基础级、2 标准级（默认）、3 增强级、10 严格级；4-9 为预留区间，视同 3。
 * 由环境变量 GEELATO_SECURITY_LEVEL 配置（GlobalContext 类加载时读取一次固化），缺省 2。
 */
@Getter
public enum SecurityLevelEnum {

    DEV(0, "开发级"),
    BASIC(1, "基础级"),
    STANDARD(2, "标准级"),
    ENHANCED(3, "增强级"),
    STRICT(10, "严格级");

    private final int code;
    private final String label;

    SecurityLevelEnum(int code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 按码值解析：0/1/2/10 精确映射，3-9 视同 ENHANCED，其余返回 null。
     */
    public static SecurityLevelEnum fromCode(int code) {
        if (code >= 3 && code <= 9) {
            return ENHANCED;
        }
        for (SecurityLevelEnum level : values()) {
            if (level.code == code) {
                return level;
            }
        }
        return null;
    }

    public boolean hasColumnEncrypt() {
        return code >= 1;
    }

    public boolean hasApiEncrypt() {
        return code >= 2;
    }

    public boolean anonymousAllowedByLevel() {
        return code < 10;
    }
}
