package cn.geelato.core.meta.spi;

import cn.geelato.core.meta.model.column.ColumnMeta;
import cn.geelato.core.meta.model.entity.TableCheck;
import cn.geelato.core.meta.model.entity.TableForeign;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.core.meta.model.view.TableView;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * 一次元数据装载返回的定义集合。
 *
 * <p>每个列表的元素类型即对应的 {@code @Entity} 定义类，字段约束由类型本身表达，
 * 实现者无需查阅数据库表结构或消费代码即可知道需要装载哪些字段：</p>
 * <ul>
 *   <li>{@link #getTableList()} — {@link TableMeta}（platform_dev_table 行）</li>
 *   <li>{@link #getColumnList()} — {@link ColumnMeta}（platform_dev_column 行）</li>
 *   <li>{@link #getViewList()} — {@link TableView}（platform_dev_view 行）</li>
 *   <li>{@link #getCheckList()} — {@link TableCheck}（platform_dev_table_check 行）</li>
 *   <li>{@link #getForeignList()} — {@link TableForeign}（platform_dev_table_foreign 行）</li>
 * </ul>
 */
@Getter
public class MetaDefinitionBundle {
    private final List<TableMeta> tableList;
    private final List<ColumnMeta> columnList;
    private final List<TableView> viewList;
    private final List<TableCheck> checkList;
    private final List<TableForeign> foreignList;

    public MetaDefinitionBundle(List<TableMeta> tableList,
                                List<ColumnMeta> columnList,
                                List<TableView> viewList,
                                List<TableCheck> checkList,
                                List<TableForeign> foreignList) {
        this.tableList = tableList == null ? Collections.emptyList() : tableList;
        this.columnList = columnList == null ? Collections.emptyList() : columnList;
        this.viewList = viewList == null ? Collections.emptyList() : viewList;
        this.checkList = checkList == null ? Collections.emptyList() : checkList;
        this.foreignList = foreignList == null ? Collections.emptyList() : foreignList;
    }

}
