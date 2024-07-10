package cn.geelato.orm.meta.model.entity;

/**
 * 实体可排序
 *
 * @author geemeta
 */
public interface EntitySortable {

    // 实现类中设置的注解模板
    // @Title(title = "次序")
    // @Col(name = "orderNo")
    long getSeqNo();

    void setSeqNo(long seqNo);
}
