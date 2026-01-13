package cn.geelato.security;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Role {
    private String Id;
    private String code;
    private String name;
    private String type;
    private String tenantCode;
}
