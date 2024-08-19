package cn.geelato.web.platform.m.security.entity;

import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.annotation.Transient;
import cn.geelato.core.meta.model.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * @author diabl
 */
@Setter
@Getter
@Entity(name = "platform_permission")
public class Permission extends BaseEntity {

    @Title(title = "应用Id")
    @Col(name = "app_id")
    private String appId;

    private String name;
    private String code;
    private String type;
    private String object;

    @Title(title = "规则")
    @Col(name = "rule", charMaxlength = 1024)
    private String rule;

    private String description;

    @Transient
    private boolean isDefault;
    private boolean workMark;
}
