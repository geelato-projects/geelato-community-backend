package cn.geelato.web.platform.arco.tree;

import java.io.Serializable;

/**
 * @author diabl
 * @description: Arco Design Tree
 * @date 2023/6/19 10:47
 */
public class TreeNodeData implements Serializable {
    private String key;// 唯一标示
    private String title;//该节点显示的标题
    private Boolean selectable = false;// 是否允许选中
    private Boolean disabled = false;//是否禁用节点
    private Boolean isLeaf = false;// 是否是叶子节点。动态加载时有效
    private TreeNodeData[] children;// 子节点

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Boolean getSelectable() {
        return selectable;
    }

    public void setSelectable(Boolean selectable) {
        this.selectable = selectable;
    }

    public Boolean getDisabled() {
        return disabled;
    }

    public void setDisabled(Boolean disabled) {
        this.disabled = disabled;
    }

    public Boolean getLeaf() {
        return isLeaf;
    }

    public void setLeaf(Boolean leaf) {
        isLeaf = leaf;
    }

    public TreeNodeData[] getChildren() {
        return children;
    }

    public void setChildren(TreeNodeData[] children) {
        this.children = children;
    }
}
