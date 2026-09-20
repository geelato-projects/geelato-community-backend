package cn.geelato.web.platform.srv.security.service;

import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.meta.Org;
import cn.geelato.meta.OrgRoleMap;
import cn.geelato.meta.Role;
import cn.geelato.web.platform.srv.platform.service.BaseService;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 组织角色关系管理（角色挂组织）。
 * <p>语义见 cn.geelato.security.OrgRole 契约：仅直属部门生效，成员不自动继承角色。
 */
@Component
public class OrgRoleMapService extends BaseService {

    /**
     * 根据组织ID和角色ID查询组织角色映射列表
     *
     * @param orgId 组织ID，多个以逗号分隔
     * @param roleId 角色ID，多个以逗号分隔
     * @return 组织角色映射列表
     */
    public List<OrgRoleMap> queryModelByIds(String orgId, String roleId) {
        List<OrgRoleMap> list = new ArrayList<>();
        if (Strings.isNotBlank(orgId) && Strings.isNotBlank(roleId)) {
            FilterGroup filter = new FilterGroup();
            filter.addFilter("orgId", FilterGroup.Operator.in, orgId);
            filter.addFilter("roleId", FilterGroup.Operator.in, roleId);
            list = this.queryModel(OrgRoleMap.class, filter);
        }

        return list;
    }

    /**
     * 批量，不重复插入组织角色映射关系
     *
     * @param model 组织角色映射关系对象
     * @return 插入的组织角色映射关系列表
     * @throws RuntimeException 当组织或角色信息为空时抛出异常
     */
    public List<OrgRoleMap> insertModels(OrgRoleMap model) {
        List<Org> orgs = this.getModelsById(Org.class, model.getOrgId());
        if (orgs == null || orgs.size() == 0) {
            throw new RuntimeException(ApiErrorMsg.IS_NULL);
        }
        List<Role> roles = this.getModelsById(Role.class, model.getRoleId());
        if (roles == null || roles.size() == 0) {
            throw new RuntimeException(ApiErrorMsg.IS_NULL);
        }
        List<OrgRoleMap> existMaps = this.queryModelByIds(model.getOrgId(), model.getRoleId());
        List<OrgRoleMap> list = new ArrayList<>();
        for (Org org : orgs) {
            for (Role role : roles) {
                boolean isExist = false;
                for (OrgRoleMap map : existMaps) {
                    if (org.getId().equals(map.getOrgId()) && role.getId().equals(map.getRoleId())) {
                        isExist = true;
                        break;
                    }
                }
                if (!isExist) {
                    OrgRoleMap orgRoleMap = new OrgRoleMap();
                    orgRoleMap.setOrgId(org.getId());
                    orgRoleMap.setOrgName(org.getName());
                    orgRoleMap.setRoleId(role.getId());
                    orgRoleMap.setRoleName(role.getName());
                    orgRoleMap = this.createModel(orgRoleMap);
                    list.add(orgRoleMap);
                }
            }
        }

        return list;
    }

    /**
     * 切换组织角色挂靠关系：已存在则删除，不存在则创建
     */
    public void switchModel(OrgRoleMap model) {
        List<Org> orgs = this.getModelsById(Org.class, model.getOrgId());
        if (orgs == null || orgs.size() == 0) {
            throw new RuntimeException(ApiErrorMsg.IS_NULL);
        }
        List<Role> roles = this.getModelsById(Role.class, model.getRoleId());
        if (roles == null || roles.size() == 0) {
            throw new RuntimeException(ApiErrorMsg.IS_NULL);
        }
        List<OrgRoleMap> existMaps = this.queryModelByIds(model.getOrgId(), model.getRoleId());
        for (Org org : orgs) {
            for (Role role : roles) {
                OrgRoleMap existMap = null;
                for (OrgRoleMap map : existMaps) {
                    if (org.getId().equals(map.getOrgId()) && role.getId().equals(map.getRoleId())) {
                        existMap = map;
                        break;
                    }
                }
                if (existMap != null) {
                    this.isDeleteModel(existMap);
                } else {
                    OrgRoleMap orgRoleMap = new OrgRoleMap();
                    orgRoleMap.setOrgId(org.getId());
                    orgRoleMap.setOrgName(org.getName());
                    orgRoleMap.setRoleId(role.getId());
                    orgRoleMap.setRoleName(role.getName());
                    this.createModel(orgRoleMap);
                }
            }
        }
    }

    /**
     * 获取组织挂靠的角色
     *
     * @param orgId 组织ID
     * @param tenantCode 租户代码，空则取当前会话
     * @return 组织挂靠的角色列表
     */
    public List<Role> queryRoleByOrg(String orgId, String tenantCode) {
        List<Role> result = new ArrayList<>();
        if (Strings.isBlank(orgId)) {
            return result;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("orgId", orgId);
        params.put("tenantCode", Strings.isNotBlank(tenantCode) ? tenantCode : getSessionTenantCode());
        List<OrgRoleMap> orgRoleMaps = queryModel(OrgRoleMap.class, params);
        if (orgRoleMaps != null && orgRoleMaps.size() > 0) {
            List<Role> roleList = this.getModelsById(Role.class, joinIds(orgRoleMaps, "roleId"));
            if (roleList != null) {
                result.addAll(roleList);
            }
        }

        return result;
    }

    private String joinIds(List<OrgRoleMap> maps, String idField) {
        List<String> ids = new ArrayList<>();
        for (OrgRoleMap map : maps) {
            String id = "roleId".equals(idField) ? map.getRoleId() : map.getOrgId();
            if (Strings.isNotBlank(id) && !ids.contains(id)) {
                ids.add(id);
            }
        }
        return String.join(",", ids);
    }
}
