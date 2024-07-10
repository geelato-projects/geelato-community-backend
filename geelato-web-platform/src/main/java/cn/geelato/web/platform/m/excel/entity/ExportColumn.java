package cn.geelato.web.platform.m.excel.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * @author diabl
 * @description: 导出excel表头
 * @date 2024/5/24 9:45
 */
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

    public String getDataIndex() {
        return dataIndex;
    }

    public void setDataIndex(String dataIndex) {
        this.dataIndex = dataIndex;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getWidth() {
        return width;
    }

    public void setWidth(long width) {
        this.width = width;
    }

    public String getAlign() {
        return align;
    }

    public void setAlign(String align) {
        this.align = align;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<ExportColumn> getChildren() {
        return children;
    }

    public void setChildren(List<ExportColumn> children) {
        this.children = children;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getBreadth() {
        return breadth;
    }

    public void setBreadth(int breadth) {
        this.breadth = breadth;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public int getFirstRow() {
        return firstRow;
    }

    public void setFirstRow(int firstRow) {
        this.firstRow = firstRow;
    }

    public int getLastRow() {
        return lastRow;
    }

    public void setLastRow(int lastRow) {
        this.lastRow = lastRow;
    }

    public int getFirstCol() {
        return firstCol;
    }

    public void setFirstCol(int firstCol) {
        this.firstCol = firstCol;
    }

    public int getLastCol() {
        return lastCol;
    }

    public void setLastCol(int lastCol) {
        this.lastCol = lastCol;
    }

    /**
     * 计算节点的
     *
     * @param currentLevel
     * @return
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
     * 计算节点有多少层
     *
     * @return
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
     * 计算高度
     *
     * @param maxDepth
     * @return
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
