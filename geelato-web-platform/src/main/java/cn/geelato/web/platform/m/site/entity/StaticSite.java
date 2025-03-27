package cn.geelato.web.platform.m.site.entity;

import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.core.meta.model.entity.EntityEnableAble;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Title(title = "静态站点")
@Entity(name = "platform_static_site")
public class StaticSite extends BaseEntity implements EntityEnableAble {
    @Title(title = "应用ID")
    @Col(name = "app_id")
    private String appId;
    @Title(title = "名称")
    private String name;
    @Title(title = "编码")
    private String code;
    @Title(title = "是否需要权限控制")
    @Col(name = "require_permission")
    private boolean requirePermission = false;
    @Title(title = "描述")
    private String description;
    @Title(title = "状态")
    @Col(name = "enable_status")
    private int enableStatus;
}
