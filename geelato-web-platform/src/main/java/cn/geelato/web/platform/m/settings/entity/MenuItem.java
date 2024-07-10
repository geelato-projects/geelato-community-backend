package cn.geelato.web.platform.m.settings.entity;


import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import cn.geelato.core.meta.model.entity.EntityTreeAble;

@Entity(name = "platform_menu_item")
@Title(title = "菜单项", description = "菜单项与菜单子项")
public class MenuItem extends BaseSortableEntity implements EntityTreeAble {
    private String title;
    private String clazz;
    private String active;
    private String linkType;
    private String href;
    private String pageCode;
    private String treeNodeId;

    @Title(title = "标题")
    @Col(name = "title", nullable = false)
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Title(title = "样式类")
    @Col(name = "clazz")
    public String getClazz() {
        return clazz;
    }

    public void setClazz(String clazz) {
        this.clazz = clazz;
    }

    @Title(title = "激活")
    @Col(name = "active")
    public String getActive() {
        return active;
    }

    public void setActive(String active) {
        this.active = active;
    }

    @Title(title = "链接类型", description = "dynamicPage|other，dynamicPage为基于设计器配置的页面。")
    @Col(name = "link_type", charMaxlength = 20)
    public String getLinkType() {
        return linkType;
    }

    public void setLinkType(String linkType) {
        this.linkType = linkType;
    }

    @Title(title = "链接")
    @Col(name = "href")
    public String getHref() {
        return href;
    }

    public void setHref(String href) {
        this.href = href;
    }


    @Title(title = "链接页面", description = "链接打开的页面编码，@see PageConfig")
    @Col(name = "page_code")
    public String getPageCode() {
        return pageCode;
    }

    public void setPageCode(String pageCode) {
        this.pageCode = pageCode;
    }

    @Title(title = "树节点id")
    @Col(name = "tree_node_id")
    @Override
    public String getTreeNodeId() {
        return treeNodeId;
    }

    @Override
    public String setTreeNodeId(String treeNodeId) {
        return this.treeNodeId = treeNodeId;
    }
}
