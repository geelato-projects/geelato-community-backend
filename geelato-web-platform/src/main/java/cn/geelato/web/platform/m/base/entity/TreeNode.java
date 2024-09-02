package cn.geelato.web.platform.m.base.entity;


import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * @author itechgee@126.com
 */
@Getter
@Setter
@Entity(name = "platform_tree_node")
@Title(title = "菜单")
public class TreeNode extends BaseSortableEntity {

    @Title(title = "树实体", description = "节点所属树对应的业务实体，例如，对于项目文件树，该实体为项目（platform_project）。")
    @Col(name = "tree_entity", nullable = true)
    private String treeEntity;
    @Title(title = "树Id", description = "树对应业务实体某条记录的id值，例如，对于项目文件树，该treeId的值为项目id，这样就可以通过项目id获取整个项目文件树。")
    @Col(name = "tree_id", nullable = false)
    private String treeId;
    @Title(title = "父节点Id，采用字符串格式，解决数字类型太大，在web端展示失真的问题")
    private String pid;
    @Title(title = "节点类型")
    private String type;
    @Title(title = "节点标题")
    private String text;
    @Title(title = "节点图标")
    @Col(name = "icon_type", nullable = true)
    private String iconType;
    @Title(title = "扩展实体", description = "扩展实体，如叶子节点对应的文件表名、业务表名")
    @Col(name = "extend_entity", nullable = true)
    private String extendEntity;
    @Title(title = "扩展实体ID", description = "扩展实体id，如叶子节点对应的文件id、表单id")
    @Col(name = "extend_id", nullable = true)
    private String extendId;
    @Title(title = "节点扩展元信息", description = "更多的扩展信息，json格式字符串")
    private String meta;
    @Title(title = "标志旗", description = "信号旗，标志旗，用于对该节点做特别的标识，如该节点作为菜单项")
    private String flag;
    @Title(title = "链接", description = "通过菜单项打开第三方链接")
    private String url;
    @Title(title = "描述")
    private String description;
}
