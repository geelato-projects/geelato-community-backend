package cn.geelato.web.platform.m.arco.entity;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * @author diabl
 * Arco Design Tree
 */
@Setter
@Getter
public class TreeNodeData implements Serializable {
    private String key;// 唯一标示
    private String title;// 该节点显示的标题
    private Boolean selectable = true;// 是否允许选中
    private Boolean disabled = false;// 是否禁用节点
    private Boolean isLeaf = false;// 是否是叶子节点。动态加载时有效
    private TreeNodeData[] children;// 子节点
    private Object data;// 其他数据
    private Integer level;// 节点层级
}
