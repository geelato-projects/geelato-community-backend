package cn.geelato.web.platform.m.excel.entity;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * @author diabl
 * @description: 导出excel表头
 */
@Getter
@Setter
public class ExportColumn {
    private String dataIndex; // 数据key，对应导出excel中的var
    private String title; // 列标题，对应导出excel中的placeholder，不包括符号${}的部分
    private String align; // 列标题的对齐，默认为center，'left' | 'center' | 'right'
    private String description; // 导出excel列上的注释信息
    private long width;
    List<ExportColumn> children = new LinkedList<>();

    private int level; // 所属第几层
    private int depth; // 多少行
    private int breadth; // 多少列

    private int firstRow; // 开始行
    private int lastRow; // 结束行
    private int firstCol; // 开始列
    private int lastCol; // 结束列

    /**
     * 计算节点的层级和宽度
     * <p>
     * 该方法用于计算给定节点的层级和宽度。层级从0开始计数，宽度表示当前层级下节点的数量。
     *
     * @param currentLevel 当前节点的层级
     * @return 返回当前节点的宽度
     */
    public int calculateLevelAndBreadth(int currentLevel) {
        this.level = currentLevel;
        this.breadth = 0;
        if (this.children != null && this.children.size() > 0) {
            for (ExportColumn child : this.children) {
                this.breadth += child.calculateLevelAndBreadth(this.level + 1);
            }
        } else {
            this.breadth = 1;
        }

        return this.breadth;
    }

    /**
     * 计算树形结构中节点的最大层级
     * <p>
     * 递归遍历树形结构，计算并返回树中节点的最大层级。
     *
     * @return 返回树形结构中节点的最大层级
     */
    public int findMaxValueInTree() {
        // 当前节点的值
        int maxValue = this.level;
        // 遍历子节点，递归调用findMaxValueInTree方法
        if (this.children != null && this.children.size() > 0) {
            for (ExportColumn child : this.children) {
                int childMaxValue = child.findMaxValueInTree();
                // 更新最大值
                if (childMaxValue > maxValue) {
                    maxValue = childMaxValue;
                }
            }
        }
        // 返回最大值
        return maxValue;
    }

    /**
     * 计算深度
     * <p>
     * 根据给定的最大深度，计算当前节点的深度。
     *
     * @param maxDepth 最大深度，表示树的最大深度
     * @return 返回当前节点的深度值
     */
    public int calculateDepth(int maxDepth) {
        int maxChildDepth = 0; // 子节点站的宽度
        if (this.children != null && this.children.size() > 0) {
            List<Integer> depths = new ArrayList<>();
            for (ExportColumn child : this.children) {
                depths.add(child.calculateDepth(maxDepth));
            }
            Collections.sort(depths);
            maxChildDepth = depths.get(depths.size() - 1);
        }
        this.depth = maxDepth - maxChildDepth - this.level;

        return this.depth;
    }
}
