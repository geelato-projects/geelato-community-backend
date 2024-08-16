package cn.geelato.web.platform.m.base.entity;


import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import lombok.Setter;

/**
 * @author itechgee@126.com
 * @date 2017/9/8.
 */
@Setter
@Entity(name = "platform_tree_node", table = "platform_tree_node")
@Title(title = "菜单")
public class TreeNode extends BaseSortableEntity {

    private String treeEntity;
    private String treeId;
    /**
     * 采用字符串格式，解决数字类型太大，在web端展示失真的问题
     */
    private String pid;
    private String type;
    private String text;
    private String iconType;
    private String extendEntity;
    private String extendId;
    private String meta;
    private String flag;

    private String url;
    private String description;

    @Col(name = "tree_entity", nullable = true)
    @Title(title = "树实体", description = "节点所属树对应的业务实体，例如，对于项目文件树，该实体为项目（platform_project）。")
    public String getTreeEntity() {
        return treeEntity;
    }

    @Col(name = "tree_id", nullable = false)
    @Title(title = "树Id", description = "树对应业务实体某条记录的id值，例如，对于项目文件树，该treeId的值为项目id，这样就可以通过项目id获取整个项目文件树。")
    public String getTreeId() {
        return treeId;
    }

    @Col(name = "type", nullable = false)
    @Title(title = "节点类型")
    public String getType() {
        return type;
    }

    @Col(name = "text", nullable = false)
    @Title(title = "节点标题")
    public String getText() {
        return text;
    }

    @Col(name = "icon_type", nullable = true)
    @Title(title = "节点图标")
    public String getIconType() {
        return iconType;
    }

    @Col(name = "pid", nullable = true)
    @Title(title = "父节点Id")
    public String getPid() {
        return pid;
    }

    @Col(name = "extend_entity", nullable = true)
    @Title(title = "扩展实体", description = "扩展实体，如叶子节点对应的文件表名、业务表名")
    public String getExtendEntity() {
        return extendEntity;
    }

    @Col(name = "extend_id", nullable = true)
    @Title(title = "扩展实体ID", description = "扩展实体id，如叶子节点对应的文件id、表单id")
    public String getExtendId() {
        return extendId;
    }

    @Col(name = "flag", nullable = true)
    @Title(title = "标志旗", description = "信号旗，标志旗，用于对该节点做特别的标识，如该节点作为菜单项")
    public String getFlag() {
        return flag;
    }

    @Col(name = "url", nullable = true)
    @Title(title = "链接", description = "通过菜单项打开第三方链接")
    public String getUrl() {
        return url;
    }

    @Col(name = "meta", nullable = true)
    @Title(title = "节点扩展元信息", description = "更多的扩展信息，json格式字符串")
    public String getMeta() {
        return meta;
    }

    @Title(title = "描述")
    public String getDescription() {
        return description;
    }
}
