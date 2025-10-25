package cn.geelato.meta;


import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity(name = "platform_menu_item")
@Title(title = "菜单项", description = "菜单项与菜单子项")
public class MenuItem extends BaseSortableEntity {
    @Title(title = "标题")
    private String title;
    @Title(title = "样式类")
    private String clazz;
    @Title(title = "激活")
    private String active;
    @Title(title = "链接类型", description = "dynamicPage|other，dynamicPage为基于设计器配置的页面。")
    @Col(name = "link_type", charMaxlength = 20)
    private String linkType;
    @Title(title = "链接")
    private String href;
    @Title(title = "链接页面", description = "链接打开的页面编码，@see PageConfig")
    @Col(name = "page_code")
    private String pageCode;
    @Title(title = "树节点id")
    @Col(name = "tree_node_id")
    private String treeNodeId;
}
