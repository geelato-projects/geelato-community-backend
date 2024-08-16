package cn.geelato.web.platform.m.excel.entity;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;


/**
 * @author diabl
 */
@Getter
@Setter
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
}
