package cn.geelato.security;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrgCore {
    private String id;
    private String pid;
    private String name;
    private String fullName;
    private String deptId;
    private String companyId;
    private String tenantCode;

    public OrgCore() {

    }

    public OrgCore(String id, String pid, String name, String fullName, String deptId, String companyId, String tenantCode) {
        this.id = id;
        this.pid = pid;
        this.name = name;
        this.fullName = fullName;
        this.deptId = deptId;
        this.companyId = companyId;
        this.tenantCode = tenantCode;
    }
}
