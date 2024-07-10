package cn.geelato.core.meta.model.entity;

import cn.geelato.core.meta.EntityType;

/**
 *  轻量的实体元数据信息。
 *  完整的实体信息
 *  @see EntityMeta
 */
public class EntityLiteMeta {
    // 实体的编码，如：user_info
    private String entityName;
    // 实体的中文名称，如：用户信息
    private String entityTitle;

    private String entityType;


    public EntityLiteMeta(String entityName, String entityTitle,EntityType entityType) {
        this.entityName = entityName;
        this.entityTitle = entityTitle;
        this.entityType=entityType.toString();
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public String getEntityTitle() {
        return entityTitle;
    }

    public void setEntityTitle(String entityTitle) {
        this.entityTitle = entityTitle;
    }


    public String getEntityType() {
        return entityType;
    }
}
