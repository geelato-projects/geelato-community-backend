package cn.geelato.security;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserOrg extends OrgCore {
    private Boolean defaultOrg;

    public UserOrg() {

    }

    public UserOrg(String fullName, String deptId, String companyId, String extendId) {
        super(null, null, fullName, fullName, deptId, companyId, extendId, null);
    }
}
