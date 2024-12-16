package cn.geelato.core.env.entity;

import cn.geelato.core.meta.model.entity.EntitySortable;
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
    private String buId;
    private String cooperatingOrgId;
    private List<UserOrg> orgs;
    private List<UserRole> roles;

    private List<UserMenu> menus;

    private List<Permission> dataPermissions;

    private List<Permission> elementPermissions;

    public Permission getDataPermissionByEntity(String entity) {
        //根据weight权重排序，取第一条
        List<Permission> entityPermission = this.dataPermissions.stream().filter(x -> x.getEntity().equals(entity)).toList();
        List<Permission> maxRoleWeightPermissionList=null;
        Permission rtnPermission=null;
        Optional<Permission> maxRoleWeightPermission = entityPermission.stream().max(Comparator.comparing(Permission::getRoleWeight));
        if(maxRoleWeightPermission.isPresent()){
           int maxRoleWeight=maxRoleWeightPermission.get().getRoleWeight();
           maxRoleWeightPermissionList=entityPermission.stream().filter(x->x.getRoleWeight()==maxRoleWeight).toList();
        }
        if(maxRoleWeightPermissionList!=null){
            rtnPermission= maxRoleWeightPermissionList.stream().max(Comparator.comparing(Permission::getWeight)).orElse(null);
        }
        return rtnPermission;
    }

}
