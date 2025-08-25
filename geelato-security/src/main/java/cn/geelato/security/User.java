package cn.geelato.security;

import lombok.Getter;
import lombok.Setter;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Getter
@Setter
public class User extends UserCore{
    private String enName;
    private int sex;
    private String orgId;
    private String password;
    private String salt;
    private String avatar;
    private String mobilePrefix;
    private String mobilePhone;
    private String telephone;
    private String email;
    private String post;
    private int type;
    private int source;
    private String nationCode;
    private String provinceCode;
    private String cityCode;
    private String address;
    private String description;
    private String jobNumber;
    private String buId;
    private String cooperatingOrgId;
    private int enableStatus;
    private boolean stocked;
    private String unionId;
    private String deptId;
    private String defaultOrgId;
    private String defaultOrgName;


    //todo define this is wrong?
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
