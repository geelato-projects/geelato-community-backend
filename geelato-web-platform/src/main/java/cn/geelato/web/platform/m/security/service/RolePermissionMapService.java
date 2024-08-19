package cn.geelato.web.platform.m.security.service;

import cn.geelato.core.gql.parser.FilterGroup;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.field.ColumnMeta;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.web.platform.enums.PermissionTypeEnum;
import cn.geelato.web.platform.m.base.service.BaseService;
import cn.geelato.web.platform.m.model.service.DevTableColumnService;
import cn.geelato.web.platform.m.security.entity.Permission;
import cn.geelato.web.platform.m.security.entity.Role;
import cn.geelato.web.platform.m.security.entity.RolePermissionMap;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.*;

/**
 * @author diabl
 */
@Component
public class RolePermissionMapService extends BaseService {
    private final MetaManager metaManager = MetaManager.singleInstance();
    @Autowired
    private RoleService roleService;
    @Autowired
    private PermissionService permissionService;
    @Autowired
    private DevTableColumnService devTableColumnService;

    /**
     * 根据角色ID和权限ID查询角色权限映射关系列表
     *
     * @param roleId       角色ID
     * @param permissionId 权限ID
     * @return 角色权限映射关系列表
     */
    public List<RolePermissionMap> queryModelByIds(String roleId, String permissionId) {
        List<RolePermissionMap> list = new ArrayList<>();
        if (Strings.isNotBlank(roleId) && Strings.isNotBlank(permissionId)) {
            FilterGroup filter = new FilterGroup();
            filter.addFilter("roleId", FilterGroup.Operator.in, roleId);
            filter.addFilter("permissionId", FilterGroup.Operator.in, permissionId);
            list = this.queryModel(RolePermissionMap.class, filter);
        }

        return list;
    }

    /**
     * 批量插入角色权限映射关系
     *
     * @param model 角色权限映射关系对象
     * @return 插入的角色权限映射关系列表
     * @throws RuntimeException 当角色或权限信息为空时抛出异常
     */
    public List<RolePermissionMap> insertModels(RolePermissionMap model) {
        // 角色存在，
        List<Role> roles = roleService.getModelsById(Role.class, model.getRoleId());
        if (roles == null || roles.size() == 0) {
            throw new RuntimeException(ApiErrorMsg.IS_NULL);
        }
        // 用户信息，
        List<Permission> permissions = permissionService.getModelsById(Permission.class, model.getPermissionId());
        if (permissions == null || permissions.size() == 0) {
            throw new RuntimeException(ApiErrorMsg.IS_NULL);
        }
        // 角色用户信息，
        List<RolePermissionMap> maps = this.queryModelByIds(model.getRoleId(), model.getPermissionId());
        // 对比插入
        List<RolePermissionMap> list = new ArrayList<>();
        for (Role role : roles) {
            for (Permission permission : permissions) {
                boolean isExist = false;
                if (maps != null && maps.size() > 0) {
                    for (RolePermissionMap map : maps) {
                        if (role.getId().equals(map.getRoleId()) && permission.getId().equals(map.getPermissionId())) {
                            isExist = true;
                            break;
                        }
                    }
                }
                if (!isExist) {
                    RolePermissionMap map = this.createByRoleAndPermission(role, permission);
                    list.add(map);
                }
            }
        }

        return list;
    }

    public void switchModel(RolePermissionMap model) {
        // 角色存在，
        List<Role> roles = roleService.getModelsById(Role.class, model.getRoleId());
        if (roles == null || roles.size() == 0) {
            throw new RuntimeException(ApiErrorMsg.IS_NULL);
        }
        // 用户信息，
        List<Permission> permissions = permissionService.getModelsById(Permission.class, model.getPermissionId());
        if (permissions == null || permissions.size() == 0) {
            throw new RuntimeException(ApiErrorMsg.IS_NULL);
        }
        // 角色用户信息，
        List<RolePermissionMap> maps = this.queryModelByIds(model.getRoleId(), model.getPermissionId());
        // 对比插入
        List<RolePermissionMap> list = new ArrayList<>();
        for (Role role : roles) {
            for (Permission permission : permissions) {
                boolean isExist = false;
                if (maps != null && maps.size() > 0) {
                    for (RolePermissionMap map : maps) {
                        if (role.getId().equals(map.getRoleId()) && permission.getId().equals(map.getPermissionId())) {
                            isExist = true;
                            this.isDeleteModel(map);
                        }
                    }
                }
                if (!isExist) {
                    this.createByRoleAndPermission(role, permission);
                }
            }
        }
    }

    /**
     * 权限进行分类，适合模型权限，dp，mp
     *
     * @param permissions
     * @return
     */
    private Set<Map<String, Object>> permissionClassify(List<Permission> permissions) {
        Set<Map<String, Object>> permissionMapSet = new LinkedHashSet<>();
        Map<String, Object> viewPermissionMap = new LinkedHashMap<>();
        viewPermissionMap.put("type", "view");
        Set<Permission> viewPermissions = new LinkedHashSet<>();
        viewPermissionMap.put("data", viewPermissions);
        permissionMapSet.add(viewPermissionMap);

        Map<String, Object> editPermissionMap = new LinkedHashMap<>();
        editPermissionMap.put("type", "edit");
        Set<Permission> editPermissions = new LinkedHashSet<>();
        editPermissionMap.put("data", editPermissions);
        permissionMapSet.add(editPermissionMap);

        Map<String, Object> customPermissionMap = new LinkedHashMap<>();
        customPermissionMap.put("type", "custom");
        Set<Permission> customPermissions = new LinkedHashSet<>();
        customPermissionMap.put("data", customPermissions);
        permissionMapSet.add(customPermissionMap);
        for (Permission model : permissions) {
            if (model.isPerDefault()) {
                boolean isEdit = false;
                for (String clazz : PermissionService.PERMISSION_MODEL_CLASSIFY) {
                    if (String.format("%s&%s", model.getObject(), clazz).equalsIgnoreCase(model.getCode())) {
                        editPermissions.add(model);
                        isEdit = true;
                        break;
                    }
                }
                if (!isEdit) {
                    viewPermissions.add(model);
                }
            } else {
                customPermissions.add(model);
            }
        }
        viewPermissionMap.put("data", permissionSort(viewPermissions, PermissionService.PERMISSION_DATA_ORDER));
        editPermissionMap.put("data", permissionSort(editPermissions, PermissionService.PERMISSION_MODEL_CLASSIFY));

        return permissionMapSet;
    }

    /**
     * 权限按照编码排序
     *
     * @param permissions
     * @param orders      编码组
     * @return
     */
    private Set<Permission> permissionSort(Set<Permission> permissions, String[] orders) {
        Set<Permission> orderPermissions = new LinkedHashSet<>();
        List<Permission> sortedPermissions = new ArrayList<>(permissions);
        for (String order : orders) {
            for (Permission model : sortedPermissions) {
                String code = String.format("%s&%s", model.getObject(), order.toLowerCase(Locale.ENGLISH));
                if (code.equalsIgnoreCase(model.getCode())) {
                    orderPermissions.add(model);
                    break;
                }
            }
        }

        return orderPermissions;
    }

    /**
     * 表格权限
     *
     * @param type
     * @param object
     * @param appId
     * @param tenantCode
     * @return
     */
    public Map<String, JSONArray> queryTablePermissions(String type, String object, String appId, String tenantCode) {
        tenantCode = Strings.isNotBlank(tenantCode) ? tenantCode : getSessionTenantCode();
        Map<String, JSONArray> tablePermissionMap = new HashMap<>();
        // 表头，表格权限
        FilterGroup tableFilter = new FilterGroup();
        tableFilter.addFilter("type", FilterGroup.Operator.in, type);
        tableFilter.addFilter("object", object);
        tableFilter.addFilter("tenantCode", tenantCode);
        List<Permission> permissions = permissionService.queryModel(Permission.class, tableFilter);
        // 默认权限
        List<Permission> defaultPermissions = permissionService.getDefaultTypePermission(type);
        // 默认字段
        List<String> permissionIds = new ArrayList<>();
        if (permissions != null && permissions.size() > 0) {
            for (Permission model : permissions) {
                model.setPerDefault(permissionService.isDefault(model, defaultPermissions));
                permissionIds.add(model.getId());
            }
            Set<Map<String, Object>> permissionMap = permissionClassify(permissions);
            tablePermissionMap.put("permission", JSON.parseArray(JSON.toJSONString(permissionMap)));
        } else {
            tablePermissionMap.put("permission", null);
        }
        // 第一列，角色
        Map<String, Object> roleParams = new HashMap<>();
        roleParams.put("appId", appId);
        roleParams.put("tenantCode", tenantCode);
        List<Role> roles = roleService.queryRoles(roleParams);
        List<String> roleIds = new ArrayList<>();
        if (roles != null && roles.size() > 0) {
            for (Role model : roles) {
                roleIds.add(model.getId());
            }
            tablePermissionMap.put("role", JSON.parseArray(JSON.toJSONString(roles)));
        } else {
            tablePermissionMap.put("role", null);
        }
        // 数据
        List<RolePermissionMap> rolePermissionMaps = new ArrayList<>();
        if (permissionIds.size() > 0 && roleIds.size() > 0) {
            FilterGroup filter = new FilterGroup();
            filter.addFilter("permissionId", FilterGroup.Operator.in, Strings.join(permissionIds, ','));
            filter.addFilter("roleId", FilterGroup.Operator.in, Strings.join(roleIds, ','));
            filter.addFilter("tenantCode", tenantCode);
            rolePermissionMaps = queryModel(RolePermissionMap.class, filter);
        }
        // 构建表格数据
        List<Map<String, Object>> tableMapList = new ArrayList<>();
        if (roles != null && roles.size() > 0) {
            for (Role role : roles) {
                Map<String, Object> tableMap = new HashMap<>();
                tableMap.put("id", role.getId());
                tableMap.put("appName", role.getAppName());
                tableMap.put("appId", role.getAppId());
                tableMap.put("enableStatus", role.getEnableStatus());
                tableMap.put("name", role.getName());
                tableMap.put("code", role.getCode());
                tableMap.put("type", role.getType());
                tableMap.put("weight", role.getWeight());
                tableMap.put("description", role.getDescription());
                if (permissions != null && permissions.size() > 0) {
                    for (Permission permission : permissions) {
                        tableMap.put(permission.getId(), false);
                        if (rolePermissionMaps != null && rolePermissionMaps.size() > 0) {
                            for (RolePermissionMap model : rolePermissionMaps) {
                                if (role.getId().equals(model.getRoleId()) && permission.getId().equals(model.getPermissionId())) {
                                    tableMap.put(permission.getId(), true);
                                    break;
                                }
                            }
                        }
                    }
                }
                tableMapList.add(tableMap);
            }
        }
        tablePermissionMap.put("table", tableMapList.size() > 0 ? JSON.parseArray(JSON.toJSONString(tableMapList)) : null);

        return tablePermissionMap;
    }

    /**
     * 表格权限
     *
     * @param tableName
     * @param appId
     * @param tenantCode
     * @return
     */
    public Map<String, JSONArray> queryColumnPermissions(String type, String tableName, String appId, String tenantCode) {
        tenantCode = Strings.isNotBlank(tenantCode) ? tenantCode : getSessionTenantCode();
        Map<String, JSONArray> tablePermissionMap = new HashMap<>();
        // 默认字段
        List<ColumnMeta> defaultColumnMetaList = metaManager.getDefaultColumn();
        List<String> defaultColumnNames = new ArrayList<>();
        for (ColumnMeta meta : defaultColumnMetaList) {
            defaultColumnNames.add(meta.getName());
        }
        // 表头
        FilterGroup colFilter = new FilterGroup();
        colFilter.addFilter("tableName", tableName);
        colFilter.addFilter("tenantCode", tenantCode);
        colFilter.addFilter("name", FilterGroup.Operator.notin, Strings.join(defaultColumnNames, ','));
        List<ColumnMeta> columnMetas = devTableColumnService.queryModel(ColumnMeta.class, colFilter);
        // 模型字段
        List<String> columnObjects = new ArrayList<>();
        if (columnMetas != null && columnMetas.size() > 0) {
            for (ColumnMeta model : columnMetas) {
                columnObjects.add(tableName + ":" + model.getName());
            }
            tablePermissionMap.put("column", JSON.parseArray(JSON.toJSONString(columnMetas)));
        } else {
            tablePermissionMap.put("column", null);
        }
        // 表格权限
        List<Permission> permissions = new ArrayList<>();
        if (columnObjects != null && columnObjects.size() > 0) {
            FilterGroup filter = new FilterGroup();
            filter.addFilter("type", type);
            filter.addFilter("object", FilterGroup.Operator.in, Strings.join(columnObjects, ','));
            filter.addFilter("tenantCode", tenantCode);
            permissions = permissionService.queryModel(Permission.class, filter);
        }
        // 默认字段
        List<String> permissionIds = new ArrayList<>();
        if (permissions != null && permissions.size() > 0) {
            for (Permission model : permissions) {
                permissionIds.add(model.getId());
            }
        }
        // 第一列，角色
        Map<String, Object> roleParams = new HashMap<>();
        roleParams.put("appId", appId);
        roleParams.put("tenantCode", tenantCode);
        List<Role> roles = roleService.queryRoles(roleParams);
        List<String> roleIds = new ArrayList<>();
        if (roles != null && roles.size() > 0) {
            for (Role model : roles) {
                roleIds.add(model.getId());
            }
            tablePermissionMap.put("role", JSON.parseArray(JSON.toJSONString(roles)));
        } else {
            tablePermissionMap.put("role", null);
        }
        // 数据
        List<RolePermissionMap> rolePermissionMaps = new ArrayList<>();
        if (permissionIds.size() > 0 && roleIds.size() > 0) {
            FilterGroup filter = new FilterGroup();
            filter.addFilter("permissionId", FilterGroup.Operator.in, Strings.join(permissionIds, ','));
            filter.addFilter("roleId", FilterGroup.Operator.in, Strings.join(roleIds, ','));
            filter.addFilter("tenantCode", tenantCode);
            rolePermissionMaps = queryModel(RolePermissionMap.class, filter);
        }
        // 构建表格数据
        List<Map<String, Object>> tableMapList = new ArrayList<>();
        if (roles != null && roles.size() > 0) {
            for (Role role : roles) {
                Map<String, Object> tableMap = new HashMap<>();
                tableMap.put("id", role.getId());
                tableMap.put("appName", role.getAppName());
                tableMap.put("appId", role.getAppId());
                tableMap.put("enableStatus", role.getEnableStatus());
                tableMap.put("name", role.getName());
                tableMap.put("code", role.getCode());
                tableMap.put("type", role.getType());
                tableMap.put("weight", role.getWeight());
                tableMap.put("description", role.getDescription());
                if (columnMetas != null && columnMetas.size() > 0) {
                    for (ColumnMeta columnMeta : columnMetas) {
                        tableMap.put(columnMeta.getId(), String.valueOf(0));
                        if (rolePermissionMaps != null && rolePermissionMaps.size() > 0) {
                            for (RolePermissionMap model : rolePermissionMaps) {
                                if (role.getId().equals(model.getRoleId())) {
                                    if (permissions != null && permissions.size() > 0) {
                                        for (Permission permission : permissions) {
                                            if (String.format("%s:%s", columnMeta.getTableName(), columnMeta.getName()).equals(permission.getObject()) && model.getPermissionId().equals(permission.getId())) {
                                                tableMap.put(columnMeta.getId(), permission.getRule());
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                tableMapList.add(tableMap);
            }
        }
        tablePermissionMap.put("table", tableMapList.size() > 0 ? JSON.parseArray(JSON.toJSONString(tableMapList)) : null);

        return tablePermissionMap;
    }

    public void insertTablePermission(RolePermissionMap form) {
        if (Strings.isNotBlank(form.getRoleId()) && Strings.isNotBlank(form.getPermissionId())) {
            Map<String, Object> params = new HashMap<>();
            params.put("roleId", form.getRoleId());
            params.put("permissionId", form.getPermissionId());
            params.put("tenantCode", Strings.isNotBlank(form.getTenantCode()) ? form.getTenantCode() : getSessionTenantCode());
            List<RolePermissionMap> maps = queryModel(RolePermissionMap.class, params);
            if (maps != null && maps.size() > 0) {
                for (RolePermissionMap map : maps) {
                    this.isDeleteModel(map);
                }
            } else {
                insertModels(form);
            }
        } else {
            throw new RuntimeException(ApiErrorMsg.PARAMETER_MISSING);
        }
    }

    public void insertTableViewPermission(RolePermissionMap form) {
        if (Strings.isNotBlank(form.getRoleId()) && Strings.isNotBlank(form.getPermissionIds())) {
            FilterGroup filter = new FilterGroup();
            filter.addFilter("roleId", form.getRoleId());
            filter.addFilter("permissionId", FilterGroup.Operator.in, form.getPermissionIds());
            filter.addFilter("tenantCode", Strings.isNotBlank(form.getTenantCode()) ? form.getTenantCode() : getSessionTenantCode());
            List<RolePermissionMap> maps = queryModel(RolePermissionMap.class, filter);
            // 删除所有权限
            if (maps != null && maps.size() > 0) {
                for (RolePermissionMap map : maps) {
                    this.isDeleteModel(map);
                }
            }
            // 传入 需要打开的权限
            if (Strings.isNotBlank(form.getPermissionId())) {
                insertModels(form);
            }
        } else {
            throw new RuntimeException(ApiErrorMsg.PARAMETER_MISSING);
        }
    }

    public void insertColumnPermission(String roleId, String columnId, String rule) {
        // 模型字段；
        ColumnMeta column = getModel(ColumnMeta.class, columnId);
        Assert.notNull(column, ApiErrorMsg.IS_NULL);
        // 角色
        Role role = getModel(Role.class, roleId);
        Assert.notNull(role, ApiErrorMsg.IS_NULL);
        // 默认权限
        List<Permission> defPermissions = permissionService.getDefaultPermission(PermissionService.PERMISSION_COLUMN_JSON);
        // 规则对应权限，
        List<Permission> permissionList = new ArrayList<>();
        FilterGroup filter = new FilterGroup();
        filter.addFilter("type", PermissionTypeEnum.COLUMN.getValue());
        filter.addFilter("object", String.format("%s:%s", column.getTableName(), column.getName()));
        filter.addFilter("tenantCode", getSessionTenantCode());
        List<Permission> permissions = queryModel(Permission.class, filter);
        // 默认字段
        List<String> permissionIds = new ArrayList<>();
        if (permissions != null && permissions.size() > 0) {
            for (Permission model : permissions) {
                permissionIds.add(model.getId());
                permissionList.add(model);
            }
        }
        // 如果不存在，新建
        if (defPermissions != null && defPermissions.size() > 0) {
            for (Permission dModel : defPermissions) {
                Permission permission = new Permission();
                permission.setAppId(permission.getAppId());
                permission.setName(dModel.getName());
                permission.setCode(String.format("%s:%s%s", column.getTableName(), column.getName(), dModel.getCode()));
                permission.setType(PermissionTypeEnum.COLUMN.getValue());
                permission.setObject(String.format("%s:%s", column.getTableName(), column.getName()));
                permission.setRule(dModel.getRule());
                permission.setDescription(dModel.getDescription());
                boolean isExist = false;
                if (permissionList != null && permissionList.size() > 0) {
                    for (Permission cModel : permissionList) {
                        if (permission.getCode().equals(cModel.getCode()) && permission.getObject().equals(cModel.getObject())) {
                            isExist = true;
                            cModel.setAppId(column.getAppId());
                            cModel.setName(permission.getName());
                            cModel.setDescription(permission.getDescription());
                            cModel.setRule(permission.getRule());
                            updateModel(cModel);
                            break;
                        }
                    }
                }
                if (!isExist) {
                    Permission map = createModel(permission);
                    permission.setId(map.getId());
                    permissionList.add(permission);
                }
            }
        }
        // 删除当前角色权限
        if (permissionIds != null && permissionIds.size() > 0) {
            FilterGroup filter1 = new FilterGroup();
            filter1.addFilter("roleId", role.getId());
            filter1.addFilter("permissionId", FilterGroup.Operator.in, Strings.join(permissionIds, ','));
            filter1.addFilter("tenantCode", getSessionTenantCode());
            List<RolePermissionMap> rolePermissionMaps = queryModel(RolePermissionMap.class, filter1);
            if (rolePermissionMaps != null && rolePermissionMaps.size() > 0) {
                for (RolePermissionMap dModel : rolePermissionMaps) {
                    this.isDeleteModel(dModel);
                }
            }
        }
        // 修改
        for (Permission permission : permissionList) {
            if (permission.getRule().equals(rule)) {
                RolePermissionMap map = new RolePermissionMap();
                map.setRoleId(role.getId());
                map.setRoleName(role.getName());
                map.setAppId(column.getAppId());
                map.setPermissionId(permission.getId());
                map.setPermissionName(permission.getName());
                createModel(map);
                break;
            }
        }
    }

    public void createAllRoleOfDefaultPermission(Permission permission) {
        Map<String, Object> roleParams = new HashMap<>();
        roleParams.put("appId", permission.getAppId());
        roleParams.put("tenantCode", permission.getTenantCode());
        List<Role> roles = roleService.queryRoles(roleParams);
        if (roles != null && roles.size() > 0) {
            for (Role role : roles) {
                RolePermissionMap map = new RolePermissionMap();
                map.setRoleId(role.getId());
                map.setRoleName(role.getName());
                map.setPermissionId(permission.getId());
                map.setPermissionName(permission.getName());
                map.setAppId(permission.getAppId());
                map.setTenantCode(permission.getTenantCode());
                createModel(map);
            }
        }
    }

    public RolePermissionMap createByRoleAndPermission(Role role, Permission permission) {
        RolePermissionMap map = new RolePermissionMap();
        map.setRoleId(role.getId());
        map.setRoleName(role.getName());
        map.setPermissionId(permission.getId());
        map.setPermissionName(permission.getName());
        map.setAppId(permission.getAppId());
        map.setTenantCode(permission.getTenantCode());
        return createModel(map);
    }
}
