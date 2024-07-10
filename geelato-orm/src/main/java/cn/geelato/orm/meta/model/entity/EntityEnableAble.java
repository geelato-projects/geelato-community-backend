package cn.geelato.orm.meta.model.entity;

/**
 * 实体具有启用状态
 *
 * @author geemeta
 */
public interface EntityEnableAble {

    // 实现类中设置的注解模板
    // @Title(title = "启用状态",description = "1表示启用、0表示未启用")
    // @Col(name = "enabled", nullable = false, dataType = "tinyint", numericPrecision = 1)
    int getEnableStatus();

    /**
     * @param enableStatus
     */
    void setEnableStatus(int enableStatus);
}
