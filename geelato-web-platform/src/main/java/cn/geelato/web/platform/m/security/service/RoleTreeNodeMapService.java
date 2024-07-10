package cn.geelato.web.platform.m.security.service;

import cn.geelato.web.platform.m.security.entity.Role;
import cn.geelato.web.platform.m.security.entity.RoleTreeNodeMap;
import org.apache.logging.log4j.util.Strings;
import cn.geelato.core.constants.ApiErrorMsg;
import cn.geelato.core.gql.parser.FilterGroup;
import cn.geelato.web.platform.m.base.entity.TreeNode;
import cn.geelato.web.platform.m.base.service.BaseService;
import cn.geelato.web.platform.m.base.service.TreeNodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @author diabl
 */
@Component
public class RoleTreeNodeMapService extends BaseService {
    @Autowired
    private RoleService roleService;
    @Autowired
    private TreeNodeService treeNodeService;

    /**
     * 根据角色ID和菜单ID查询角色菜单映射关系列表
     *
     * @param roleId     角色ID
     * @param treeNodeId 菜单ID
     * @return 角色菜单映射关系列表
     */
    public List<RoleTreeNodeMap> queryModelByIds(String roleId, String treeNodeId) {
        List<RoleTreeNodeMap> list = new ArrayList<>();
        if (Strings.isNotBlank(roleId) && Strings.isNotBlank(treeNodeId)) {
            FilterGroup filter = new FilterGroup();
            filter.addFilter("roleId", FilterGroup.Operator.in, roleId);
            filter.addFilter("treeNodeId", FilterGroup.Operator.in, treeNodeId);
            list = this.queryModel(RoleTreeNodeMap.class, filter);
        }
        return list;
    }

    /**
     * 批量，不重复插入角色菜单映射关系
     *
     * @param model 角色菜单映射关系对象
     * @return 插入的角色菜单映射关系列表
     * @throws RuntimeException 当角色或菜单信息为空时抛出异常
     */
    public List<RoleTreeNodeMap> insertModels(RoleTreeNodeMap model) {
        // 角色存在，
        List<Role> roles = roleService.getModelsById(Role.class, model.getRoleId());
        if (roles == null || roles.size() == 0) {
            throw new RuntimeException(ApiErrorMsg.IS_NULL);
        }
        // 菜单存在，
        List<TreeNode> treeNodes = treeNodeService.getModelsById(TreeNode.class, model.getTreeNodeId());
        if (treeNodes == null || treeNodes.size() == 0) {
            throw new RuntimeException(ApiErrorMsg.IS_NULL);
        }
        // 角色用户信息，
        List<RoleTreeNodeMap> maps = this.queryModelByIds(model.getRoleId(), model.getTreeNodeId());
        // 对比插入
        List<RoleTreeNodeMap> list = new ArrayList<>();
        for (Role role : roles) {
            for (TreeNode treeNode : treeNodes) {
                boolean isExist = false;
                if (maps != null && maps.size() > 0) {
                    for (RoleTreeNodeMap map : maps) {
                        if (role.getId().equals(map.getRoleId()) && treeNode.getId().equals(map.getTreeNodeId())) {
                            isExist = true;
                            break;
                        }
                    }
                }
                if (!isExist) {
                    RoleTreeNodeMap map = new RoleTreeNodeMap();
                    map.setRoleId(role.getId());
                    map.setRoleName(role.getName());
                    map.setTreeNodeId(treeNode.getId());
                    map.setTreeNodeText(treeNode.getText());
                    map.setTitle(treeNode.getText());
                    map.setTenantCode(role.getTenantCode());
                    map.setAppId(treeNode.getTreeId());
                    map.setTreeId(treeNode.getTreeId());
                    map = this.createModel(map);
                    list.add(map);
                }
            }
        }

        return list;
    }

    public void cancelModels(RoleTreeNodeMap model) {
        // 角色用户信息，
        List<RoleTreeNodeMap> maps = this.queryModelByIds(model.getRoleId(), model.getTreeNodeId());
        // 对比插入
        if (maps != null && maps.size() > 0) {
            for (RoleTreeNodeMap map : maps) {
                this.isDeleteModel(map);
            }
        }
    }
}
