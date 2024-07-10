package cn.geelato.web.platform.m.excel.entity;

import java.util.List;
import java.util.Map;

public class RowMeta {

    /**
     * 是否需按多组进行迭代的行
     */
    private boolean isMultiGroupRow;
    private boolean isDeleteGroupRow;
    private List<CellMeta> notListCellIndexes;

    /**
     * 将列表与非列表cellMeta分组，存在不同的List中
     * 列表类占位符单元格索引位置Map，列表变量名为key，列表为value
     */
    private Map<String, List<CellMeta>> listCellMetaMap;

    public boolean isMultiGroupRow() {
        return isMultiGroupRow;
    }

    public void setMultiGroupRow(boolean multiGroupRow) {
        isMultiGroupRow = multiGroupRow;
    }

    public boolean isDeleteGroupRow() {
        return isDeleteGroupRow;
    }

    public void setDeleteGroupRow(boolean deleteGroupRow) {
        isDeleteGroupRow = deleteGroupRow;
    }

    /**
     * 非列表类单元格(动态需解释，带有占位符的单元格)索引位置集合
     *
     * @return
     */
    public List<CellMeta> getNotListCellIndexes() {
        return notListCellIndexes;
    }

    public void setNotListCellIndexes(List<CellMeta> notListCellIndexes) {
        this.notListCellIndexes = notListCellIndexes;
    }

    public Map<String, List<CellMeta>> getListCellMetaMap() {
        return listCellMetaMap;
    }

    public void setListCellMetaMap(Map<String, List<CellMeta>> listCellMetaMap) {
        this.listCellMetaMap = listCellMetaMap;
    }
}
