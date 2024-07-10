package cn.geelato.web.platform.m.excel.entity;

/**
 *  占位符元数据
 *  用于word或excel的占位符替换
 */
public class CellMeta {
    private int index;

    private PlaceholderMeta placeholderMeta;

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public PlaceholderMeta getPlaceholderMeta() {
        return placeholderMeta;
    }

    public void setPlaceholderMeta(PlaceholderMeta placeholderMeta) {
        this.placeholderMeta = placeholderMeta;
    }
}
