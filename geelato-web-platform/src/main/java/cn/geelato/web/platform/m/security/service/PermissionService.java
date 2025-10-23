package cn.geelato.web.platform.m.security.service;

import cn.geelato.core.constants.ResourcesFiles;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.meta.model.column.ColumnMeta;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.utils.FastJsonUtils;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.enums.PermissionTypeEnum;
import cn.geelato.web.platform.m.base.service.BaseSortableService;
import cn.geelato.meta.Permission;
import cn.geelato.meta.Role;
import cn.geelato.meta.RolePermissionMap;
import com.alibaba.fastjson2.JSON;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

/**
 * @author diabl
 */
@Component
public class PermissionService extends BaseSortableService {
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
     * <p>
     * 删除指定的权限模型，并处理相关的角色权限关系。
     *
     * @param model 要删除的权限模型对象
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
     * 判断是否是默认权限
     * <p>
     * 根据提供的权限模型，判断其是否为默认权限。
     *
     * @param model 权限模型对象
     * @return 如果是默认权限，则返回true；否则返回false
     */
    public boolean isDefault(Permission model) {
        List<Permission> defaultPermissions = getDefaultTypePermission(model.getType());

        return isDefault(model, defaultPermissions);
    }

    /**
     * 判断给定的权限是否为默认权限
     * <p>
     * 根据提供的权限模型对象和默认权限列表，判断该权限是否为默认权限。
     *
     * @param model              权限模型对象，包含权限的类型、编码和对象等信息
     * @param defaultPermissions 默认权限列表，用于与提供的权限模型对象进行对比
     * @return 如果给定的权限是默认权限，则返回true；否则返回false
     */
    public boolean isDefault(Permission model, List<Permission> defaultPermissions) {
        boolean isDef = false;
        if (Strings.isBlank(model.getCode()) || Strings.isBlank(model.getType()) || Strings.isBlank(model.getObject())) {
            return isDef;
        }
        if (defaultPermissions != null && !defaultPermissions.isEmpty()) {
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
     * <p>
     * 根据传入的权限类型，获取对应的默认权限列表。
     *
     * @param type 权限类型，可以是数据权限、模型权限、列权限等
     * @return 返回对应类型的默认权限列表
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
     * <p>
     * 从指定的json文件中读取默认权限列表。
     *
     * @param jsonFile 包含默认权限列表的json文件的路径
     * @return 返回读取到的默认权限列表，如果读取失败则返回空列表
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
     * 修改表格和字段权限的对象名称
     * <p>
     * 根据提供的当前对象名称和旧对象名称，修改与之相关的表格和字段权限的对象名称。
     *
     * @param curObject 当前的对象名称
     * @param sorObject 旧的对象名称
     */
    public void tablePermissionChangeObject(String curObject, String sorObject, String parentObject) {
        List<Permission> permissions = new ArrayList<>();
        // 表格权限
        FilterGroup tableFilter = new FilterGroup();
        tableFilter.addFilter("type", FilterGroup.Operator.in, PermissionTypeEnum.getTablePermissions());
        tableFilter.addFilter("parentObject", parentObject);
        tableFilter.addFilter("object", sorObject);
        tableFilter.addFilter("tenantCode", getSessionTenantCode());
        List<Permission> tPermissions = queryModel(Permission.class, tableFilter);
        // 修改 object
        if (tPermissions != null && !tPermissions.isEmpty()) {
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
        columnFilter.addFilter("parentObject", parentObject);
        columnFilter.addFilter("object", FilterGroup.Operator.startWith, String.format("%s:", sorObject));
        columnFilter.addFilter("tenantCode", getSessionTenantCode());
        List<Permission> cPermissions = queryModel(Permission.class, columnFilter);
        // 修改 object
        if (cPermissions != null && !cPermissions.isEmpty()) {
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
     * 更改字段权限对象
     * <p>
     * 根据提供的表格名称、新字段和旧字段，更新对应的字段权限对象。
     *
     * @param tableName 表格名称
     * @param curObject 新字段名称
     * @param sorObject 旧字段名称
     */
    public void columnPermissionChangeObject(String connectId, String tableName, String curObject, String sorObject) {
        // 字段权限
        Map<String, Object> params = new HashMap<>();
        params.put("type", PermissionTypeEnum.COLUMN.getValue());
        params.put("parentObject", connectId);
        params.put("object", String.format("%s:%s", tableName, sorObject));
        params.put("tenantCode", getSessionTenantCode());
        List<Permission> permissions = queryModel(Permission.class, params);
        // 修改 object
        if (permissions != null && !permissions.isEmpty()) {
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
     * <p>
     * 根据传入的权限类型、对象和应用ID，重置对应的默认权限。
     *
     * @param type   权限类型，如模型权限、数据权限等
     * @param object 权限对象，如表名、模型名等
     * @param appId  应用ID，用于标识需要重置权限的应用
     * @throws RuntimeException 如果传入的权限类型不合法，则抛出异常
     */
    public void resetDefaultPermission(String type, String object, String parentObject, String appId) {
        if (PermissionTypeEnum.MODEL.getValue().equals(type) ||
                PermissionTypeEnum.DATA.getValue().equalsIgnoreCase(type) ||
                PermissionTypeEnum.getTablePermissions().equalsIgnoreCase(type)) {
            resetTableDefaultPermission(type, object, parentObject, appId);
        } else if (PermissionTypeEnum.COLUMN.getValue().equals(type)) {
            resetColumnDefaultPermission(type, object, parentObject, appId);
        } else {
            throw new RuntimeException("[type] non-being");
        }
    }

    /**
     * 重置模型默认权限，包括数据权限和模型权限
     * <p>
     * 根据提供的权限类型、对象名称和应用ID，重置对应模型的默认权限。
     *
     * @param types  权限类型，可以是数据权限或模型权限
     * @param object 对象名称
     * @param appId  应用ID
     */
    public void resetTableDefaultPermission(String types, String object, String parentObject, String appId) {
        // 当前权限
        FilterGroup tableFilter = new FilterGroup();
        tableFilter.addFilter("type", FilterGroup.Operator.in, types);
        tableFilter.addFilter("parentObject", parentObject);
        tableFilter.addFilter("object", object);
        tableFilter.addFilter("tenantCode", getSessionTenantCode());
        List<Permission> curPermissions = queryModel(Permission.class, tableFilter);
        // 默认权限
        List<Permission> defPermissions = getDefaultTypePermission(types);
        if (defPermissions != null && !defPermissions.isEmpty()) {
            if (curPermissions != null && !curPermissions.isEmpty()) {
                for (Permission dModel : defPermissions) {
                    boolean isExist = false;
                    for (Permission cModel : curPermissions) {
                        if (cModel.getCode().equals(String.format("%s%s", cModel.getObject(), dModel.getCode()))) {
                            cModel.setName(dModel.getName());
                            cModel.setRule(dModel.getRule());
                            cModel.setAppId(appId);
                            cModel.setParentObject(parentObject);
                            cModel.setSeqNo(dModel.getSeqNo());
                            cModel.setDescription(dModel.getDescription());
                            updateModel(cModel);
                            isExist = true;
                            break;
                        }
                    }
                    if (!isExist) {
                        createDefaultPermission(parentObject, object, dModel);
                    }
                }
            } else {
                for (Permission dModel : defPermissions) {
                    dModel.setAppId(appId);
                    createDefaultPermission(parentObject, object, dModel);
                }
            }
        }
    }

    public void resetTableDefaultPermissionByRole(String types, String parentObject, String object, String appId) {
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
        tableFilter.addFilter("parentObject", parentObject);
        tableFilter.addFilter("object", object);
        tableFilter.addFilter("tenantCode", getSessionTenantCode());
        List<Permission> permissionList = queryModel(Permission.class, tableFilter);
        List<String> permissionIds = new ArrayList<>();
        for (Permission permission : permissionList) {
            permissionIds.add(permission.getId());
        }
        // 给当前角色添加模型权限
        if (!roleIds.isEmpty() && !permissionIds.isEmpty()) {
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
     * 初始化，创建默认权限
     * <p>
     * 根据提供的对象和默认权限模型，创建对应的默认权限。
     *
     * @param object 权限对象，如表名、模型名等
     * @param dModel 默认权限模型，包含权限的编码等信息
     */
    private void createDefaultPermission(String parentObject, String object, Permission dModel) {
        String defaultCode = dModel.getCode();
        dModel.setParentObject(parentObject);
        dModel.setObject(object);
        dModel.setCode(String.format("%s%s", object, defaultCode));
        Permission permission = createModel(dModel);
        if (Arrays.asList(PERMISSION_DEFAULT_TO_ROLE).contains(defaultCode)) {
            // rolePermissionMapService.createAllRoleOfDefaultPermission(JSON.parseObject(JSON.toJSONString(permission), Permission.class));
        }
    }

    /**
     * 重置模型字段默认权限
     * <p>
     * 根据提供的权限类型、模型名称和应用ID，重置模型字段的默认权限。
     *
     * @param type      权限类型，如"cp"表示列权限
     * @param tableName 模型名称，即需要重置权限的表名
     * @param appId     应用ID，用于标识需要重置权限的应用
     */
    public void resetColumnDefaultPermission(String type, String tableName, String parentObject, String appId) {
        // 表
        Map<String, Object> tabParams = new HashMap<>();
        tabParams.put("connectId", parentObject);
        tabParams.put("entityName", tableName);
        tabParams.put("tenantCode", getSessionTenantCode());
        List<TableMeta> tableMetas = queryModel(TableMeta.class, tabParams);
        if (tableMetas == null || tableMetas.isEmpty()) {
            throw new RuntimeException("表不存在");
        }
        TableMeta tableMeta = tableMetas.get(0);
        // 表头
        Map<String, Object> colParams = new HashMap<>();
        colParams.put("tableId", tableMeta.getId());
        colParams.put("tenantCode", getSessionTenantCode());
        List<ColumnMeta> columnMetas = queryModel(ColumnMeta.class, colParams);
        // 默认字段
        List<String> columnObjects = new ArrayList<>();
        if (columnMetas != null && !columnMetas.isEmpty()) {
            for (ColumnMeta model : columnMetas) {
                columnObjects.add(tableName + ":" + model.getName());
            }
        }
        // 当前权限
        List<Permission> permissions = new ArrayList<>();
        if (!columnObjects.isEmpty()) {
            FilterGroup filter = new FilterGroup();
            filter.addFilter("type", type);
            filter.addFilter("parentObject", tableMeta.getConnectId());
            filter.addFilter("object", FilterGroup.Operator.in, Strings.join(columnObjects, ','));
            filter.addFilter("tenantCode", getSessionTenantCode());
            permissions = queryModel(Permission.class, filter);
        }
        // 默认字段
        List<String> permissionIds = new ArrayList<>();
        if (permissions != null && !permissions.isEmpty()) {
            for (Permission model : permissions) {
                permissionIds.add(model.getId());
            }
        }
        // 默认权限
        List<Permission> defPermissions = getDefaultTypePermission(type);
        // 构建权限
        if (columnMetas != null && !columnMetas.isEmpty()) {
            for (ColumnMeta column : columnMetas) {
                if (defPermissions != null && !defPermissions.isEmpty()) {
                    for (Permission dModel : defPermissions) {
                        Permission permission = new Permission();
                        permission.setName(dModel.getName());
                        permission.setAppId(appId);
                        permission.setCode(String.format("%s:%s%s", column.getTableName(), column.getName(), dModel.getCode()));
                        permission.setType(type);
                        permission.setParentObject(tableMeta.getConnectId());
                        permission.setObject(String.format("%s:%s", column.getTableName(), column.getName()));
                        permission.setRule(dModel.getRule());
                        permission.setDescription(dModel.getDescription());
                        boolean isExist = false;
                        if (permissions != null && !permissions.isEmpty()) {
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
        if (!permissionIds.isEmpty()) {
            FilterGroup filter = new FilterGroup();
            filter.addFilter("permissionId", FilterGroup.Operator.in, Strings.join(permissionIds, ','));
            filter.addFilter("tenantCode", getSessionTenantCode());
            List<RolePermissionMap> rolePermissionMaps = rolePermissionMapService.queryModel(RolePermissionMap.class, filter);
            if (rolePermissionMaps != null && !rolePermissionMaps.isEmpty()) {
                for (RolePermissionMap dModel : rolePermissionMaps) {
                    rolePermissionMapService.isDeleteModel(dModel);
                }
            }
        }
    }

    public void shiftPermission(String ids) {
        List<String> idList = StringUtils.toListDr(ids);
        if (idList == null || idList.isEmpty()) {
            throw new RuntimeException("无可移动的权限");
        }
        FilterGroup filter = new FilterGroup();
        filter.addFilter("id", FilterGroup.Operator.in, String.join(",", idList));
        List<Permission> permissions = queryModel(Permission.class, filter);
        if (permissions == null || permissions.isEmpty()) {
            throw new RuntimeException("无可移动的权限");
        }
        int sqlNo = idList.size() - 1;
        for (String id : idList) {
            for (Permission permission : permissions) {
                if (permission.getId().equals(id)) {
                    permission.setSeqNo(sqlNo--);
                    updateModel(permission);
                    break;
                }
            }
        }
    }
}
