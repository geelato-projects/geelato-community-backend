package cn.geelato.core.meta.spi;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 一次元数据装载返回的定义集合。
 */
public class MetaDefinitionBundle {
    private final List<Map<String, Object>> tableList;
    private final List<Map<String, Object>> columnList;
    private final List<Map<String, Object>> viewList;
    private final List<Map<String, Object>> checkList;
    private final List<Map<String, Object>> foreignList;

    public MetaDefinitionBundle(List<Map<String, Object>> tableList,
                                List<Map<String, Object>> columnList,
                                List<Map<String, Object>> viewList,
                                List<Map<String, Object>> checkList,
                                List<Map<String, Object>> foreignList) {
        this.tableList = tableList == null ? Collections.emptyList() : tableList;
        this.columnList = columnList == null ? Collections.emptyList() : columnList;
        this.viewList = viewList == null ? Collections.emptyList() : viewList;
        this.checkList = checkList == null ? Collections.emptyList() : checkList;
        this.foreignList = foreignList == null ? Collections.emptyList() : foreignList;
    }

    public List<Map<String, Object>> getTableList() {
        return tableList;
    }

    public List<Map<String, Object>> getColumnList() {
        return columnList;
    }

    public List<Map<String, Object>> getViewList() {
        return viewList;
    }

    public List<Map<String, Object>> getCheckList() {
        return checkList;
    }

    public List<Map<String, Object>> getForeignList() {
        return foreignList;
    }
}
