package cn.geelato.web.platform.enums;

/**
 * @author diabl
 */

public enum IsDefaultOrgEnum {
    IS(1, "是"), NO(0, "否");

    private final int code;
    private final String name;

    IsDefaultOrgEnum(int code, String name) {
        this.code = code;
        this.name = name;
    }

    public int getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
}
