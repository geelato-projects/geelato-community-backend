package cn.geelato.web.platform.m.base.entity;

import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import lombok.Setter;

/**
 * @author geelato
 */
@Setter
@Entity(name = "platform_cache_item_meta")
@Title(title = "缓存定义", description = "定义缓存的结构信息，不用于数据库存储。")
public class CacheItemMeta extends BaseEntity {
    private String region;
    private String key;
    private String value;
    private int level;

    @Col(name = "region")
    public String getRegion() {
        return region;
    }

    @Col(name = "key")
    public String getKey() {
        return key;
    }

    @Col(name = "value")
    public String getValue() {
        return value;
    }

    @Col(name = "level")
    public int getLevel() {
        return level;
    }
}
