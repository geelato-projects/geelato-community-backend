package cn.geelato.web.platform.shiro;

import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;

/**
 * 自定义Authentication对象，使得Subject除了携带用户的登录名外还可以携带更多信息.
 */
public class ShiroUser implements Serializable {
    @Serial
    private static final long serialVersionUID = -1373760761780840081L;

    public String id;
    public String loginName;
    @Getter
    public String name;

    public ShiroUser(String id, String loginName, String name) {
        this.id = id;
        this.loginName = loginName;
        this.name = name;
    }

    /**
     * 本函数输出将作为默认的<shiro:principal/>输出.
     */
    @Override
    public String toString() {
        return loginName;
    }

    /**
     * 重载hashCode,只计算loginName;
     */
    @Override
    public int hashCode() {
        return Arrays.hashCode(new String[]{"loginName"});
    }

    /**
     * 重载equals,只计算loginName;
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        ShiroUser other = (ShiroUser) obj;
        if (loginName == null) {
            return other.loginName == null;
        } else return loginName.equals(other.loginName);
    }
}
