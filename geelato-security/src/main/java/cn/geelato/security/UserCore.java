package cn.geelato.security;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserCore {
    private String userId;
    private String userName;
    private String loginName;
    private String tenantCode;
}
