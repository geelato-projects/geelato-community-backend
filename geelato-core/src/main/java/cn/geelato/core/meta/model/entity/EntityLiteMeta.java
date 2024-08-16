package cn.geelato.core.meta.model.entity;

import cn.geelato.core.meta.EntityType;
import lombok.Getter;
import lombok.Setter;

/**
 * 轻量的实体元数据信息。
 * 完整的实体信息
 *
 * @see EntityMeta
 */
@Setter
@Getter
public class EntityLiteMeta {
    // 实体的编码，如：user_info
    private String entityName;
    // 实体的中文名称，如：用户信息
    private String entityTitle;

    private String entityType;


    public EntityLiteMeta(String entityName, String entityTitle, EntityType entityType) {
        this.entityName = entityName;
        this.entityTitle = entityTitle;
        this.entityType = entityType.toString();
    }
}
