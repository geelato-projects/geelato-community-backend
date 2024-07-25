package cn.geelato.core.enums;

/**
 * @author diabl
 */
public enum EnableStatusEnum {
    ENABLED(1, "启用", "enabled"), DISABLED(0, "禁用", "disabled");

    private final int code;
    private final String cnName;
    private final String enName;

    EnableStatusEnum(int code, String cnName, String enName) {
        this.code = code;
        this.cnName = cnName;
        this.enName = enName;
    }

    public int getCode() {
        return code;
    }

    public String getCnName() {
        return cnName;
    }

    public String getEnName() {
        return enName;
    }
}
