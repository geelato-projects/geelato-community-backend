package cn.geelato.web.platform.m.security.entity;


import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.annotation.Transient;
import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import cn.geelato.core.meta.model.entity.EntityEnableAble;
import lombok.Setter;

/**
 * Created by hongxueqian on 14-4-12.
 */
@Setter
@Entity(name = "platform_role")
@Title(title = "角色")
public class Role extends BaseSortableEntity implements EntityEnableAble {
    private String appId;
    private String appName;
    private String name;
    private String code;
    private String type;
    private int enableStatus = ColumnDefault.ENABLE_STATUS_VALUE;
    private String description;
    private Integer weight;
    private String appIds;
    private boolean usedApp = false;

    @Title(title = "应用")
    @Col(name = "app_id")
    public String getAppId() {
        return appId;
    }

    @Transient
    public String getAppName() {
        return appName;
    }

    @Transient
    public String getAppIds() {
        return appIds;
    }

    @Title(title = "编码")
    @Col(name = "code")
    public String getCode() {
        return code;
    }

    @Title(title = "名称")
    @Col(name = "name", nullable = false)
    public String getName() {
        return name;
    }

    @Title(title = "类型", description = "app | platform，即应用级角色、平台级角色")
    @Col(name = "type")
    public String getType() {
        return type;
    }

    @Title(title = "描述")
    @Col(name = "description", charMaxlength = 1024)
    public String getDescription() {
        return description;
    }

    @Title(title = "启用状态", description = "1表示启用、0表示未启用")
    @Col(name = "enable_status", nullable = false, dataType = "tinyint", numericPrecision = 2)
    @Override
    public int getEnableStatus() {
        return this.enableStatus;
    }

    @Title(title = "权重")
    @Col(name = "weight")
    public Integer getWeight() {
        return weight;
    }

    @Title(title = "是否用于应用")
    @Col(name = "used_app")
    public boolean getUsedApp() {
        return usedApp;
    }
}
