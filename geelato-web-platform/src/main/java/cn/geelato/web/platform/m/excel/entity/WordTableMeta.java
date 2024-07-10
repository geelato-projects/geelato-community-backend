package cn.geelato.web.platform.m.excel.entity;

import org.apache.poi.xwpf.usermodel.XWPFTable;
import cn.geelato.web.platform.enums.WordTableLoopTypeEnum;

import java.util.List;
import java.util.Map;

/**
 * @author diabl
 * @date 2024/1/9 18:01
 */
public class WordTableMeta {
    private String type;// 行循环、列循环、表循环
    private XWPFTable table;
    private String identify;
    private List<Map> valueMapList;
    private int rowTotal = 0;
    private int cellTotal = 0;
    private int rowStartPosition = -1;
    private int cellStartPosition = -1;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public XWPFTable getTable() {
        return table;
    }

    public void setTable(XWPFTable table) {
        this.table = table;
    }

    public String getIdentify() {
        return identify;
    }

    public void setIdentify(String identify) {
        this.identify = identify;
    }

    public List<Map> getValueMapList() {
        return valueMapList;
    }

    public void setValueMapList(List<Map> valueMapList) {
        this.valueMapList = valueMapList;
    }

    public int getRowTotal() {
        return rowTotal;
    }

    public void setRowTotal(int rowTotal) {
        this.rowTotal = rowTotal;
    }

    public int getCellTotal() {
        return cellTotal;
    }

    public void setCellTotal(int cellTotal) {
        this.cellTotal = cellTotal;
    }

    public int getRowStartPosition() {
        return rowStartPosition;
    }

    public void setRowStartPosition(int rowStartPosition) {
        this.rowStartPosition = rowStartPosition;
    }

    public int getCellStartPosition() {
        return cellStartPosition;
    }

    public void setCellStartPosition(int cellStartPosition) {
        this.cellStartPosition = cellStartPosition;
    }

    public boolean isLoopTypeRow() {
        return WordTableLoopTypeEnum.ROW.name().equalsIgnoreCase(this.type);
    }

    public boolean isLoopTypeCell() {
        return WordTableLoopTypeEnum.CELL.name().equalsIgnoreCase(this.type);
    }

    public boolean isLoopTypeTable() {
        return WordTableLoopTypeEnum.TABLE.name().equalsIgnoreCase(this.type);
    }
}
