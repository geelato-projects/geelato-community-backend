package cn.geelato.core.meta.model.entity;


import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;

/**
 * 可排序的基础实体
 */
@Getter
@Setter
public class BaseSortableEntity extends BaseEntity implements EntitySortable {
    @Title(title = "次序")
    @Col(name = "seq_no")
    protected long seqNo = ColumnDefault.SEQ_NO_VALUE;

}
