package cn.geelato.core.env.entity;

import lombok.Getter;
import lombok.Setter;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Setter
@Getter
public class User {
    private String userId;
    private String userName;
    private String loginName;
    private String defaultOrgId;
    private String defaultOrgName;
    private String deptId;
    private String buId;
    private String cooperatingOrgId;
    private String unionId;
    private List<UserOrg> orgs;
    private List<UserRole> roles;

    private List<UserMenu> menus;

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

}
