package cn.geelato.security;

import lombok.Getter;
import lombok.Setter;

/**
 * @author diabl
 */
@Setter
@Getter
public class Tenant {
    private String code;
    private String name;

    public Tenant(String code) {
        this.code = code;
    }
}
