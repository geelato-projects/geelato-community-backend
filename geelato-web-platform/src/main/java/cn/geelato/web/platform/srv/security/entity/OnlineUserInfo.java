package cn.geelato.web.platform.srv.security.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OnlineUserInfo {
    private String userId;
    private String loginName;
    private String userName;
    private String tenantCode;
    private String orgId;
    private String orgName;
    private String deptId;
    private String buId;
    private Long lastSeen;
}

