package cn.geelato.web.platform.srv.security.service;

import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.meta.Org;
import cn.geelato.meta.Role;
import cn.geelato.meta.User;
import cn.geelato.meta.UserOrgRoleMap;
import cn.geelato.web.platform.srv.platform.service.BaseService;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户组织角色关系管理（用户挂组织角色）。
 * <p>语义见 cn.geelato.security.UserOrgRole 契约：部门角色对用户生效的唯一途径是本表直挂，
 * 用户可以不是来源部门成员。
 */
@Component
public class UserOrgRoleMapService extends BaseService {

    /**
     * 根据用户ID、组织ID和角色ID查询用户组织角色映射列表
     *
     * @param userId 用户ID，多个以逗号分隔
     * @param orgId 组织ID，多个以逗号分隔
     * @param roleId 角色ID，多个以逗号分隔
     * @return 用户组织角色映射列表
     */
    public List<UserOrgRoleMap> queryModelByIds(String userId, String orgId, String roleId) {
        List<UserOrgRoleMap> list = new ArrayList<>();
        if (Strings.isNotBlank(userId) && Strings.isNotBlank(orgId) && Strings.isNotBlank(roleId)) {
            FilterGroup filter = new FilterGroup();
            filter.addFilter("userId", FilterGroup.Operator.in, userId);
            filter.addFilter("orgId", FilterGroup.Operator.in, orgId);
            filter.addFilter("roleId", FilterGroup.Operator.in, roleId);
            list = this.queryModel(UserOrgRoleMap.class, filter);
        }

        return list;
    }

    /**
     * 批量，不重复插入用户组织角色映射关系（userId × orgId × roleId 去重）
     *
     * @param model 用户组织角色映射关系对象
     * @return 插入的用户组织角色映射关系列表
     * @throws RuntimeException 当用户、组织或角色信息为空时抛出异常
     */
    public List<UserOrgRoleMap> insertModels(UserOrgRoleMap model) {
        List<User> users = this.getModelsById(User.class, model.getUserId());
        if (users == null || users.size() == 0) {
            throw new RuntimeException(ApiErrorMsg.IS_NULL);
        }
        List<Org> orgs = this.getModelsById(Org.class, model.getOrgId());
        if (orgs == null || orgs.size() == 0) {
            throw new RuntimeException(ApiErrorMsg.IS_NULL);
        }
        List<Role> roles = this.getModelsById(Role.class, model.getRoleId());
        if (roles == null || roles.size() == 0) {
            throw new RuntimeException(ApiErrorMsg.IS_NULL);
        }
        List<UserOrgRoleMap> existMaps = this.queryModelByIds(model.getUserId(), model.getOrgId(), model.getRoleId());
        List<UserOrgRoleMap> list = new ArrayList<>();
        for (User user : users) {
            for (Org org : orgs) {
                for (Role role : roles) {
                    boolean isExist = false;
                    for (UserOrgRoleMap map : existMaps) {
                        if (user.getId().equals(map.getUserId()) && org.getId().equals(map.getOrgId())
                                && role.getId().equals(map.getRoleId())) {
                            isExist = true;
                            break;
                        }
                    }
                    if (!isExist) {
                        UserOrgRoleMap userOrgRoleMap = new UserOrgRoleMap();
                        userOrgRoleMap.setUserId(user.getId());
                        userOrgRoleMap.setUserName(user.getName());
                        userOrgRoleMap.setOrgId(org.getId());
                        userOrgRoleMap.setOrgName(org.getName());
                        userOrgRoleMap.setRoleId(role.getId());
                        userOrgRoleMap.setRoleName(role.getName());
                        userOrgRoleMap = this.createModel(userOrgRoleMap);
                        list.add(userOrgRoleMap);
                    }
                }
            }
        }

        return list;
    }

    /**
     * 切换用户组织角色关系：已存在则删除，不存在则创建
     */
    public void switchModel(UserOrgRoleMap model) {
        List<User> users = this.getModelsById(User.class, model.getUserId());
        if (users == null || users.size() == 0) {
            throw new RuntimeException(ApiErrorMsg.IS_NULL);
        }
        List<Org> orgs = this.getModelsById(Org.class, model.getOrgId());
        if (orgs == null || orgs.size() == 0) {
            throw new RuntimeException(ApiErrorMsg.IS_NULL);
        }
        List<Role> roles = this.getModelsById(Role.class, model.getRoleId());
        if (roles == null || roles.size() == 0) {
            throw new RuntimeException(ApiErrorMsg.IS_NULL);
        }
        List<UserOrgRoleMap> existMaps = this.queryModelByIds(model.getUserId(), model.getOrgId(), model.getRoleId());
        for (User user : users) {
            for (Org org : orgs) {
                for (Role role : roles) {
                    UserOrgRoleMap existMap = null;
                    for (UserOrgRoleMap map : existMaps) {
                        if (user.getId().equals(map.getUserId()) && org.getId().equals(map.getOrgId())
                                && role.getId().equals(map.getRoleId())) {
                            existMap = map;
                            break;
                        }
                    }
                    if (existMap != null) {
                        this.isDeleteModel(existMap);
                    } else {
                        UserOrgRoleMap userOrgRoleMap = new UserOrgRoleMap();
                        userOrgRoleMap.setUserId(user.getId());
                        userOrgRoleMap.setUserName(user.getName());
                        userOrgRoleMap.setOrgId(org.getId());
                        userOrgRoleMap.setOrgName(org.getName());
                        userOrgRoleMap.setRoleId(role.getId());
                        userOrgRoleMap.setRoleName(role.getName());
                        this.createModel(userOrgRoleMap);
                    }
                }
            }
        }
    }

    /**
     * 获取用户经组织挂靠的角色（含来源部门）
     *
     * @param userId 用户ID
     * @param tenantCode 租户代码，空则取当前会话
     * @return 用户组织角色映射列表
     */
    public List<UserOrgRoleMap> queryOrgRoleByUser(String userId, String tenantCode) {
        if (Strings.isBlank(userId)) {
            return new ArrayList<>();
        }
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("tenantCode", Strings.isNotBlank(tenantCode) ? tenantCode : getSessionTenantCode());
        return queryModel(UserOrgRoleMap.class, params);
    }
}
