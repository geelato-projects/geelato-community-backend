package cn.geelato.web.platform.m.security.service;

import cn.geelato.web.platform.m.security.entity.Permission;
import cn.geelato.web.platform.m.security.entity.Role;
import cn.geelato.web.platform.m.security.entity.RolePermissionMap;
import com.alibaba.fastjson2.JSON;
import org.apache.logging.log4j.util.Strings;
import cn.geelato.core.constants.ResourcesFiles;
import cn.geelato.core.gql.parser.FilterGroup;
import cn.geelato.core.meta.model.field.ColumnMeta;
import cn.geelato.core.util.FastJsonUtils;
import cn.geelato.web.platform.enums.PermissionTypeEnum;
import cn.geelato.web.platform.m.base.service.BaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

/**
 * @author diabl
 */
@Component
public class PermissionService extends BaseService {
    public static final String PERMISSION_DATA_JSON = ResourcesFiles.PERMISSION_DATA_DEFAULT_JSON;
    public static final String PERMISSION_MODEL_JSON = ResourcesFiles.PERMISSION_MODEL_DEFAULT_JSON;
    public static final String PERMISSION_COLUMN_JSON = ResourcesFiles.PERMISSION_COLUMN_DEFAULT_JSON;
    public static final String[] PERMISSION_DEFAULT_TO_ROLE = {"&myself", "&insert", "&update", "&delete"};
    public static final String[] PERMISSION_MODEL_CLASSIFY = {"Insert", "Update", "Delete"};
    public static final String[] PERMISSION_DATA_ORDER = {"all", "myBusiness", "myDept", "myself"};
    @Lazy
    @Autowired
    private RolePermissionMapService rolePermissionMapService;
    @Lazy
    @Autowired
    private RoleService roleService;

    public Permission updateModel(Permission form) {
        // 原来的数据
        Permission model = super.getModel(Permission.class, form.getId());
        // 更新
        Permission permissionMap = super.updateModel(form);
        // 更新关联表
        Map<String, Object> params = new HashMap<>();
        params.put("permissionId", model.getName());
        if (!form.getName().equals(model.getName())) {
            List<RolePermissionMap> pList = rolePermissionMapService.queryModel(RolePermissionMap.class, params);
            if (pList != null) {
                for (RolePermissionMap rModel : pList) {
                    rModel.setPermissionName(form.getName());
                    rolePermissionMapService.updateModel(rModel);
                }
            }
        }

        return permissionMap;
    }

    /**
     * 逻辑删除
     *
     * @param model
     */
    public void isDeleteModel(Permission model) {
        // 用户删除
        super.isDeleteModel(model);
        // 角色权限关系表
        Map<String, Object> params = new HashMap<>();
        params.put("permissionId", model.getId());
        List<RolePermissionMap> rList = rolePermissionMapService.queryModel(RolePermissionMap.class, params);
        if (rList != null) {
            for (RolePermissionMap oModel : rList) {
                rolePermissionMapService.isDeleteModel(oModel);
            }
        }
    }

    /**
     * 是否是默认权限
     *
     * @param model
     * @return
     */
    public boolean isDefault(Permission model) {
        List<Permission> defaultPermissions = getDefaultTypePermission(model.getType());

        return isDefault(model, defaultPermissions);
    }

    /**
     * 是否是默认权限
     *
     * @param model
     * @param defaultPermissions 对比的权限
     * @return
     */
    public boolean isDefault(Permission model, List<Permission> defaultPermissions) {
        boolean isDef = false;
        if (Strings.isBlank(model.getCode()) || Strings.isBlank(model.getType()) || Strings.isBlank(model.getObject())) {
            return isDef;
        }
        if (defaultPermissions != null && defaultPermissions.size() > 0) {
            for (Permission permission : defaultPermissions) {
                if (model.getType().equalsIgnoreCase(permission.getType()) && model.getCode().equals(String.format("%s%s", model.getObject(), permission.getCode()))) {
                    isDef = true;
                    break;
                }
            }
        }

        return isDef;
    }

    /**
     * 根据不同类型获取默认权限
     *
     * @param type
     * @return
     */
    public List<Permission> getDefaultTypePermission(String type) {
        List<Permission> defaultPermissions = new ArrayList<>();
        // 默认权限
        List<Permission> dataJson = getDefaultPermission(PermissionService.PERMISSION_DATA_JSON);
        List<Permission> modelJson = getDefaultPermission(PermissionService.PERMISSION_MODEL_JSON);
        List<Permission> columnJson = getDefaultPermission(PermissionService.PERMISSION_COLUMN_JSON);
        // 不同类型查询
        if (PermissionTypeEnum.DATA.getValue().equalsIgnoreCase(type)) {
            defaultPermissions.addAll(dataJson);
        } else if (PermissionTypeEnum.MODEL.getValue().equalsIgnoreCase(type)) {
            defaultPermissions.addAll(modelJson);
        } else if (PermissionTypeEnum.COLUMN.getValue().equalsIgnoreCase(type)) {
            defaultPermissions.addAll(columnJson);
        } else if (PermissionTypeEnum.getTablePermissions().equalsIgnoreCase(type)) {
            defaultPermissions.addAll(dataJson);
            defaultPermissions.addAll(modelJson);
        } else if (PermissionTypeEnum.getTableAndColumnPermissions().equalsIgnoreCase(type)) {
            defaultPermissions.addAll(dataJson);
            defaultPermissions.addAll(modelJson);
            defaultPermissions.addAll(columnJson);
        }

        return defaultPermissions;
    }

    /**
     * 默认权限，读取json文件
     *
     * @param jsonFile
     * @return
     */
    public List<Permission> getDefaultPermission(String jsonFile) {
        List<Permission> defaultPermissions = new ArrayList<Permission>();

        try {
            String jsonStr = FastJsonUtils.readJsonFile(jsonFile);
            List<Permission> permissionList = JSON.parseArray(jsonStr, Permission.class);
            if (permissionList != null && !permissionList.isEmpty()) {
                for (Permission permission : permissionList) {
                    permission.afterSet();
                    defaultPermissions.add(permission);
                }
            }
        } catch (IOException e) {
            defaultPermissions = new ArrayList<>();
        }

        return defaultPermissions;
    }

    /**
     * @param curObject 新的
     * @param sorObject 旧的
     */
    public void tablePermissionChangeObject(String curObject, String sorObject) {
        List<Permission> permissions = new ArrayList<>();
        // 表格权限
        FilterGroup tableFilter = new FilterGroup();
        tableFilter.addFilter("type", FilterGroup.Operator.in, PermissionTypeEnum.getTablePermissions());
        tableFilter.addFilter("object", sorObject);
        tableFilter.addFilter("tenantCode", getSessionTenantCode());
        List<Permission> tPermissions = queryModel(Permission.class, tableFilter);
        // 修改 object
        if (tPermissions != null && tPermissions.size() > 0) {
            for (Permission permission : tPermissions) {
                // tableName&XX
                if (permission.getCode().startsWith(sorObject + "&")) {
                    permission.setCode(permission.getCode().replace(sorObject + "&", curObject + "&"));
                }
                // tableName
                permission.setObject(curObject);
                updateModel(permission);
            }
        }
        // 字段权限
        FilterGroup columnFilter = new FilterGroup();
        columnFilter.addFilter("type", PermissionTypeEnum.COLUMN.getValue());
        columnFilter.addFilter("object", FilterGroup.Operator.startWith, String.format("%s:", sorObject));
        columnFilter.addFilter("tenantCode", getSessionTenantCode());
        List<Permission> cPermissions = queryModel(Permission.class, columnFilter);
        // 修改 object
        if (cPermissions != null && cPermissions.size() > 0) {
            for (Permission permission : cPermissions) {
                // tableName:columnName&XX
                if (permission.getCode().startsWith(sorObject + ":")) {
                    permission.setCode(permission.getCode().replace(sorObject + ":", curObject + ":"));
                }
                // tableName:columnName
                if (permission.getObject().startsWith(sorObject + ":")) {
                    permission.setObject(permission.getObject().replace(sorObject + ":", curObject + ":"));
                }
                updateModel(permission);
            }
        }
    }

    /**
     * @param tableName 表格
     * @param curObject 新字段
     * @param sorObject 旧字段
     */
    public void columnPermissionChangeObject(String tableName, String curObject, String sorObject) {
        // 字段权限
        Map<String, Object> params = new HashMap<>();
        params.put("type", PermissionTypeEnum.COLUMN.getValue());
        params.put("object", String.format("%s:%s", tableName, sorObject));
        params.put("tenantCode", getSessionTenantCode());
        List<Permission> permissions = queryModel(Permission.class, params);
        // 修改 object
        if (permissions != null && permissions.size() > 0) {
            for (Permission permission : permissions) {
                String object = String.format("%s:%s", tableName, curObject);
                // tableName:columnName&XX
                if (permission.getCode().startsWith(permission.getObject())) {
                    permission.setCode(permission.getCode().replace(permission.getObject() + "&", object + "&"));
                }
                // tableName:columnName
                permission.setObject(object);
                updateModel(permission);
            }
        }
    }

    /**
     * 重置默认权限
     *
     * @param type
     * @param object
     */
    public void resetDefaultPermission(String type, String object, String appId) {
        if (PermissionTypeEnum.MODEL.getValue().equals(type) ||
                PermissionTypeEnum.DATA.getValue().equalsIgnoreCase(type) ||
                PermissionTypeEnum.getTablePermissions().equalsIgnoreCase(type)) {
            resetTableDefaultPermission(type, object, appId);
        } else if (PermissionTypeEnum.COLUMN.getValue().equals(type)) {
            resetColumnDefaultPermission(type, object, appId);
        } else {
            throw new RuntimeException("[type] non-being");
        }
    }

    /**
     * 重置模型默认权限，data，model
     *
     * @param types
     * @param object
     */
    public void resetTableDefaultPermission(String types, String object, String appId) {
        // 当前权限
        FilterGroup tableFilter = new FilterGroup();
        tableFilter.addFilter("type", FilterGroup.Operator.in, types);
        tableFilter.addFilter("object", object);
        tableFilter.addFilter("tenantCode", getSessionTenantCode());
        List<Permission> curPermissions = queryModel(Permission.class, tableFilter);
        // 默认权限
        List<Permission> defPermissions = getDefaultTypePermission(types);
        if (defPermissions != null && defPermissions.size() > 0) {
            if (curPermissions != null && curPermissions.size() > 0) {
                for (Permission dModel : defPermissions) {
                    boolean isExist = false;
                    for (Permission cModel : curPermissions) {
                        if (cModel.getCode().equals(String.format("%s%s", cModel.getObject(), dModel.getCode()))) {
                            cModel.setName(dModel.getName());
                            cModel.setRule(dModel.getRule());
                            cModel.setAppId(appId);
                            cModel.setDescription(dModel.getDescription());
                            updateModel(cModel);
                            isExist = true;
                            break;
                        }
                    }
                    if (!isExist) {
                        createDefaultPermission(object, dModel);
                    }
                }
            } else {
                for (Permission dModel : defPermissions) {
                    dModel.setAppId(appId);
                    createDefaultPermission(object, dModel);
                }
            }
        }
    }

    public void resetTableDefaultPermissionByRole(String types, String object, String appId) {
        // 当前权限
        Map<String, Object> params = new HashMap<>();
        params.put("appId", appId);
        List<Role> roleList = roleService.queryRoles(params);
        List<String> roleIds = new ArrayList<>();
        for (Role role : roleList) {
            roleIds.add(role.getId());
        }
        // 当前权限
        FilterGroup tableFilter = new FilterGroup();
        tableFilter.addFilter("type", FilterGroup.Operator.in, types);
        tableFilter.addFilter("object", object);
        tableFilter.addFilter("tenantCode", getSessionTenantCode());
        List<Permission> permissionList = queryModel(Permission.class, tableFilter);
        List<String> permissionIds = new ArrayList<>();
        for (Permission permission : permissionList) {
            permissionIds.add(permission.getId());
        }
        // 给当前角色添加模型权限
        if (roleIds.size() > 0 && permissionIds.size() > 0) {
            FilterGroup filterGroup1 = new FilterGroup();
            filterGroup1.addFilter("permissionId", FilterGroup.Operator.in, String.join(",", permissionIds));
            filterGroup1.addFilter("roleId", FilterGroup.Operator.in, String.join(",", roleIds));
            List<RolePermissionMap> rolePermissionMaps = rolePermissionMapService.queryModel(RolePermissionMap.class, filterGroup1);
            List<String> isExistIds = new ArrayList<>();
            for (RolePermissionMap map : rolePermissionMaps) {
                if (!isExistIds.contains(map.getRoleId())) {
                    isExistIds.add(map.getRoleId());
                }
            }
            for (Role role : roleList) {
                if (!isExistIds.contains(role.getId())) {
                    for (Permission permission : permissionList) {
                        for (String code : PERMISSION_DEFAULT_TO_ROLE) {
                            if (permission.getCode().equals(String.format("%s%s", permission.getObject(), code))) {
                                rolePermissionMapService.createByRoleAndPermission(role, permission);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 初始化，默认权限
     *
     * @param object
     * @param dModel
     */
    private void createDefaultPermission(String object, Permission dModel) {
        String defaultCode = dModel.getCode();
        dModel.setObject(object);
        dModel.setCode(String.format("%s%s", object, defaultCode));
        Permission permission = createModel(dModel);
        if (Arrays.asList(PERMISSION_DEFAULT_TO_ROLE).contains(defaultCode)) {
            // rolePermissionMapService.createAllRoleOfDefaultPermission(JSON.parseObject(JSON.toJSONString(permission), Permission.class));
        }
    }

    /**
     * 重置模型字段默认权限
     *
     * @param type      cp
     * @param tableName 模型名称
     */
    public void resetColumnDefaultPermission(String type, String tableName, String appId) {
        // 表头
        Map<String, Object> colParams = new HashMap<>();
        colParams.put("tableName", tableName);
        colParams.put("tenantCode", getSessionTenantCode());
        List<ColumnMeta> columnMetas = queryModel(ColumnMeta.class, colParams);
        // 默认字段
        List<String> columnObjects = new ArrayList<>();
        if (columnMetas != null && columnMetas.size() > 0) {
            for (ColumnMeta model : columnMetas) {
                columnObjects.add(tableName + ":" + model.getName());
            }
        }
        // 当前权限
        List<Permission> permissions = new ArrayList<>();
        if (columnObjects != null && columnObjects.size() > 0) {
            FilterGroup filter = new FilterGroup();
            filter.addFilter("type", type);
            filter.addFilter("object", FilterGroup.Operator.in, Strings.join(columnObjects, ','));
            filter.addFilter("tenantCode", getSessionTenantCode());
            permissions = queryModel(Permission.class, filter);
        }
        // 默认字段
        List<String> permissionIds = new ArrayList<>();
        if (permissions != null && permissions.size() > 0) {
            for (Permission model : permissions) {
                permissionIds.add(model.getId());
            }
        }
        // 默认权限
        List<Permission> defPermissions = getDefaultTypePermission(type);
        // 构建权限
        if (columnMetas != null && columnMetas.size() > 0) {
            for (ColumnMeta column : columnMetas) {
                if (defPermissions != null && defPermissions.size() > 0) {
                    for (Permission dModel : defPermissions) {
                        Permission permission = new Permission();
                        permission.setName(dModel.getName());
                        permission.setAppId(appId);
                        permission.setCode(String.format("%s:%s%s", column.getTableName(), column.getName(), dModel.getCode()));
                        permission.setType(type);
                        permission.setObject(String.format("%s:%s", column.getTableName(), column.getName()));
                        permission.setRule(dModel.getRule());
                        permission.setDescription(dModel.getDescription());
                        boolean isExist = false;
                        if (permissions != null && permissions.size() > 0) {
                            for (Permission cModel : permissions) {
                                if (permission.getCode().equals(cModel.getCode()) && permission.getObject().equals(cModel.getObject())) {
                                    isExist = true;
                                    cModel.setAppId(appId);
                                    cModel.setName(permission.getName());
                                    cModel.setDescription(permission.getDescription());
                                    cModel.setRule(permission.getRule());
                                    updateModel(cModel);
                                }
                            }
                        }
                        if (!isExist) {
                            createModel(permission);
                        }
                    }
                }
            }
        }
        // 重置角色权限
        if (permissionIds != null && permissionIds.size() > 0) {
            FilterGroup filter = new FilterGroup();
            filter.addFilter("permissionId", FilterGroup.Operator.in, Strings.join(permissionIds, ','));
            filter.addFilter("tenantCode", getSessionTenantCode());
            List<RolePermissionMap> rolePermissionMaps = queryModel(RolePermissionMap.class, filter);
            if (rolePermissionMaps != null && rolePermissionMaps.size() > 0) {
                for (RolePermissionMap dModel : rolePermissionMaps) {
                    rolePermissionMapService.isDeleteModel(dModel);
                }
            }
        }
    }
}