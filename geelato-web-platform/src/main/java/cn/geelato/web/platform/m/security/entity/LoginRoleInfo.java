package cn.geelato.web.platform.m.security.entity;

/**
 * @author geemeta
 */
public class LoginRoleInfo {
    private String roleName;
    private String value;

    public LoginRoleInfo(String roleName, String value) {
        this.roleName = roleName;
        this.value = value;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
