package cn.geelato.web.platform.m.security.enums;

import lombok.Getter;

/**
 * @author diabl
 */

@Getter
public enum IsDefaultOrgEnum {
    IS(1, "是"), NO(0, "否");

    private final int code;
    private final String name;

    IsDefaultOrgEnum(int code, String name) {
        this.code = code;
        this.name = name;
    }

}
