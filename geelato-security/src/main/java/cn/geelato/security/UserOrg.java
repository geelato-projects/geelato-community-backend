package cn.geelato.security;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserOrg extends OrgCore {


    private String fullName;
    private String deptId;
    private String companyId;
    private String extendId;
    private Boolean defaultOrg;
    private String type;
    private String category;
    private String description;
    public UserOrg() {

    }

    public UserOrg(String fullName, String deptId, String companyId, String extendId) {
        this.fullName = fullName;
        this.deptId = deptId;
        this.companyId = companyId;
        this.extendId = extendId;
    }
}
