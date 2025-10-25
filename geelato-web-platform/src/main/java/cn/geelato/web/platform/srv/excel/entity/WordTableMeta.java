package cn.geelato.web.platform.srv.excel.entity;

import lombok.Getter;
import lombok.Setter;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import cn.geelato.web.platform.srv.excel.enums.WordTableLoopTypeEnum;

import java.util.List;
import java.util.Map;

/**
 * @author diabl
 */
@Getter
@Setter
public class WordTableMeta {
    private String type;// 行循环、列循环、表循环
    private XWPFTable table;
    private String identify;
    private List<Map> valueMapList;
    private int rowTotal = 0;
    private int cellTotal = 0;
    private int rowStartPosition = -1;
    private int cellStartPosition = -1;

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
