package cn.geelato.security;

import lombok.Getter;
import lombok.Setter;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Getter
@Setter
public class User extends UserCore{

    private String jobNumber;
    private String description;
    private String orgId;   //用户直接挂靠所在的部门或科室
    private String orgName;
    private String defaultOrgId;  //默认部门
    private String defaultOrgName;
    private String cooperatingOrgId; //协作部门
    private String deptId; //当用户挂靠的是科室的时候，该值为科室的上级部门ID
    private String buId; //事业部
    private String buName;
    private String companyId;  //所属公司
    private String extendId;
    private String companyName;
    private String enName;
    private int sex;
    private String avatar;
    private String mobilePrefix;
    private String mobilePhone;
    private String telephone;
    private String email;
    private String post;
    private String nationCode;
    private String provinceCode;
    private String cityCode;
    private String address;
    private int type;
    private int source;
    private int enableStatus;
    private String weixinUnionId;
    private String weixinWorkUserId;

    private UserOrg defaultOrg;
    private Tenant tenant;

    private List<UserOrg> userOrgs;
    private List<UserRole> userRoles;

    private List<Permission> dataPermissions;
    private List<Permission> elementPermissions;

    public List<Permission> getDataPermissionByEntity_temp(String entity) {
        return this.dataPermissions.stream().filter(x -> x.getEntity().equals(entity)).toList();
    }

    public Permission getDataPermissionByEntity(String entity) {
        // 根据weight权重排序，取第一条
        if(this.dataPermissions==null)
            return null;
        List<Permission> entityPermission = this.dataPermissions.stream().filter(x -> x.getEntity().equals(entity)).toList();
        List<Permission> maxWeightPermissionList = null;
        Permission rtnPermission = null;
        Optional<Permission> maxWeightPermission = entityPermission.stream().max(Comparator.comparing(Permission::getWeight));
        if (maxWeightPermission.isPresent()) {
            int maxWeight = maxWeightPermission.get().getWeight();
            maxWeightPermissionList = entityPermission.stream().filter(x -> x.getWeight() == maxWeight).toList();
        }
        if (maxWeightPermissionList != null) {
            rtnPermission=maxWeightPermissionList.get(0);
        }
        return rtnPermission;
    }
    
    /**
     * 历史兼容入口，请优先使用 UserOrgInfoEnricher 补齐组织派生信息。
     */
    public void setupOrgInfo(OrgProvider orgProvider) {
        if (orgProvider == null) {
            return;
        }
        if (this.orgId != null && !this.orgId.isEmpty()) {
            this.orgName = orgProvider.getOrgName(this.orgId);
            this.companyId = orgProvider.getCompanyId(this.orgId);
            if (this.companyId != null && !this.companyId.isEmpty()) {
                this.companyName = orgProvider.getOrgName(this.companyId);
                this.buId = this.companyId;
                this.buName = this.companyName;
            }
            if (this.deptId == null || this.deptId.isEmpty()) {
                this.deptId = orgProvider.getDeptId(this.orgId);
            }
        }
        if (this.defaultOrgId != null && !this.defaultOrgId.isEmpty()) {
            this.defaultOrgName = orgProvider.getOrgName(this.defaultOrgId);
        }
    }
}
