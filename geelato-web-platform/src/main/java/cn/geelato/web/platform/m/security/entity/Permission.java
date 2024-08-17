package cn.geelato.web.platform.m.security.entity;

import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.annotation.Transient;
import cn.geelato.core.meta.model.entity.BaseEntity;
import lombok.Setter;

/**
 * @author diabl
 */
@Setter
@Entity(name = "platform_permission")
public class Permission extends BaseEntity {

    private String appId;
    private String name;
    private String code;
    private String type;
    private String object;
    private String rule;
    private String description;
    private boolean isDefault;

    @Title(title = "应用Id")
    @Col(name = "app_id")
    public String getAppId() {
        return appId;
    }

    @Title(title = "名称")
    @Col(name = "name")
    public String getName() {
        return name;
    }

    @Title(title = "编码")
    @Col(name = "code")
    public String getCode() {
        return code;
    }

    @Title(title = "类型")
    @Col(name = "type")
    public String getType() {
        return type;
    }

    @Title(title = "")
    @Col(name = "object")
    public String getObject() {
        return object;
    }

    @Title(title = "规则")
    @Col(name = "rule", charMaxlength = 1024)
    public String getRule() {
        return rule;
    }

    @Title(title = "名称")
    @Col(name = "description")
    public String getDescription() {
        return description;
    }

    @Title(title = "默认权限")
    @Transient
    public boolean isDefault() {
        return isDefault;
    }
}
