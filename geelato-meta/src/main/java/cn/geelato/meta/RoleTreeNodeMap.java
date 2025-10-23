package cn.geelato.meta;


import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.ForeignKey;
import cn.geelato.lang.meta.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 菜单存在platform_tree_node中，字段flag为"menuItem"
 */
@Getter
@Setter
@Entity(name = "platform_role_r_tree_node")
@Title(title = "角色菜单关系表")
public class RoleTreeNodeMap extends BaseEntity {
    @Title(title = "应用Id")
    @Col(name = "app_id")
    private String appId;
    @Title(title = "树ID")
    @Col(name = "tree_id", nullable = false)
    private String treeId;
    @Title(title = "角色ID")
    @ForeignKey(fTable = Role.class)
    @Col(name = "role_id", refTables = "platform_role", refColName = "platform_role.id")
    private String roleId;
    @Title(title = "菜单ID")
    @ForeignKey(fTable = TreeNode.class)
    @Col(name = "tree_node_id", refTables = "platform_tree_node", refColName = "platform_tree_node.id")
    private String treeNodeId;
    @Title(title = "菜单名称")
    @Col(name = "tree_node_text", isRefColumn = true, refLocalCol = "treeNodeId", refColName = "platform_tree_node.text")
    private String treeNodeText;
    @Title(title = "角色名称")
    @Col(name = "role_name", isRefColumn = true, refLocalCol = "roleId", refColName = "platform_role.name")
    private String roleName;
    @Title(title = "名称")
    private String title;
}
