package cn.geelato.web.platform.arco.tree;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * @author diabl
 * @description: Arco Design Tree
 * @date 2023/6/19 10:47
 */
public class TreeNodeData implements Serializable {
    @Setter
    @Getter
    private String key;// 唯一标示
    @Setter
    @Getter
    private String title;//该节点显示的标题
    @Setter
    @Getter
    private Boolean selectable = false;// 是否允许选中
    @Getter
    @Setter
    private Boolean disabled = false;//是否禁用节点
    private Boolean isLeaf = false;// 是否是叶子节点。动态加载时有效
    @Setter
    @Getter
    private TreeNodeData[] children;// 子节点

    public Boolean getLeaf() {
        return isLeaf;
    }

    public void setLeaf(Boolean leaf) {
        isLeaf = leaf;
    }

}
