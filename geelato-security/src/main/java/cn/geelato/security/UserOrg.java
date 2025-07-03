package cn.geelato.security;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserOrg extends OrgCore {
    private Boolean defaultOrg;

    public UserOrg() {

    }

    public UserOrg(String fullName, String deptId, String companyId) {
        super(null, null, fullName, fullName, deptId, companyId, null);
    }

    public UserOrg(String id, String pid, String name, String fullName, String deptId, String companyId, String tenantCode, Boolean defaultOrg) {
        super(id, pid, name, fullName, deptId, companyId, tenantCode);
        this.defaultOrg = defaultOrg;
    }
}
