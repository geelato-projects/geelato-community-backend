package cn.geelato.web.platform.m.security.entity;

import lombok.Getter;
import lombok.Setter;

/**
 * @author geemeta
 */
@Getter
@Setter
public class LoginRoleInfo {
    private String roleName;
    private String value;

    public LoginRoleInfo(String roleName, String value) {
        this.roleName = roleName;
        this.value = value;
    }
}
