package cn.geelato.web.platform.m.security.entity;

import cn.geelato.core.constants.MediaTypes;
import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.annotation.Transient;
import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.utils.Base64Utils;
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

    @Title(title = "对象")
    private String object;
    @Title(title = "父对象")
    @Col(name = "parent_object")
    private String parentObject;

    @Title(title = "规则")
    @Col(name = "rule", charMaxlength = 1024)
    private String rule;

    private String description;

    @Transient
    private boolean perDefault;

    @Override
    public void afterSet() {
        if (Base64Utils.isBase64(this.getRule(), MediaTypes.TEXT_PLAIN_BASE64)) {
            this.setRule(Base64Utils.decode(this.getRule()));
        }
    }
}
