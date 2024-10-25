package cn.geelato.core.meta.model.entity;

/**
 * 实体具有启用状态
 *
 * @author geemeta
 */
public interface EntityEnableAble {

    // 实现类中设置的注解模板
    int getEnableStatus();

    /**
     */
    void setEnableStatus(int enableStatus);
}
