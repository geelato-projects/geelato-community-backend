package cn.geelato.security;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserCore {
    private String id;
    private String name;
    private String loginName;
    private String tenantCode;
}
