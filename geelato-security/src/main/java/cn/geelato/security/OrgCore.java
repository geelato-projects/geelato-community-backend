package cn.geelato.security;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrgCore {
    private String orgId;
    private String code;
    private String pid;
    private String name;
    private String tenantCode;

    public OrgCore() {

    }

    public OrgCore(String id, String pid, String name,String tenantCode) {
        this.orgId = id;
        this.pid = pid;
        this.name = name;
        this.tenantCode = tenantCode;
    }
}
