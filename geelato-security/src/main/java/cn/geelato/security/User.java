package cn.geelato.security;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Getter
@Setter
@Slf4j
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

    public Permission getDataPermissionByEntity(String entity) {
        // 根据weight权重排序，取第一条
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
     * 使用OrgProvider设置用户的组织相关信息
     * 包括orgName、defaultOrgName、buName、companyName
     *
     * @param orgProvider 组织信息提供者
     * @return 当前用户实例，支持链式调用
     */
    public User setupOrgInfo(OrgProvider orgProvider) {
        try {
            // 设置当前组织名称
            if (this.orgId != null && !this.orgId.isEmpty()) {
                this.orgName = orgProvider.getOrgName(this.orgId);
            }
            
            // 设置默认组织名称
            if (this.defaultOrgId != null && !this.defaultOrgId.isEmpty()) {
                this.defaultOrgName = orgProvider.getOrgName(this.defaultOrgId);
            }
            
            // 设置事业部/公司名称
            if (this.orgId != null && !this.orgId.isEmpty()) {
                // 获取公司ID
                this.companyId = orgProvider.getCompanyId(this.orgId);
                if (this.companyId != null && !this.companyId.isEmpty()) {
                    this.companyName = orgProvider.getOrgName(this.companyId);
                    // 事业部与公司是同一概念
                    this.buId = this.companyId;
                    this.buName = this.companyName;
                }
            }
            
            // 如果deptId为空，尝试从orgId获取
            if ((this.deptId == null || this.deptId.isEmpty()) && this.orgId != null && !this.orgId.isEmpty()) {
                this.deptId = orgProvider.getDeptId(this.orgId);
            }
        } catch (Exception e) {
            log.error("设置用户组织信息失败", e);
        }
        
        return this;
    }
}
