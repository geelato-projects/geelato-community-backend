package cn.geelato.core.meta.model.entity;

/**
 * 实体具有启用状态
 *
 * @author geemeta
 */
public interface EntityEnableAble {

    /**
     * 获取是否启用注解模板的状态。
     * 在实现类中设置，用于指示是否启用特定的注解模板。
     *
     * @return 返回一个整数值，通常用于表示启用（1）或禁用（0）状态。
     */
    int getEnableStatus();

    /**
     * 设置启用状态。
     * 该方法用于设置启用状态的值。
     *
     * @param enableStatus 启用状态的值
     */
    void setEnableStatus(int enableStatus);
}
