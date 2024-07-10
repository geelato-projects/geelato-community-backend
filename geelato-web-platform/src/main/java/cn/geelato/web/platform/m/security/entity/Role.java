package cn.geelato.web.platform.m.security.entity;


import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.annotation.Transient;
import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import cn.geelato.core.meta.model.entity.EntityEnableAble;

/**
 * Created by hongxueqian on 14-4-12.
 */

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

    public void setAppId(String appId) {
        this.appId = appId;
    }

    @Transient
    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    @Transient
    public String getAppIds() {
        return appIds;
    }

    public void setAppIds(String appIds) {
        this.appIds = appIds;
    }

    @Title(title = "编码")
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    @Title(title = "名称")
    @Col(name = "name", nullable = false)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Title(title = "类型", description = "app | platform，即应用级角色、平台级角色")
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Title(title = "描述")
    @Col(name = "description", charMaxlength = 1024)
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Title(title = "启用状态", description = "1表示启用、0表示未启用")
    @Col(name = "enable_status", nullable = false, dataType = "tinyint", numericPrecision = 2)
    @Override
    public int getEnableStatus() {
        return this.enableStatus;
    }

    @Override
    public void setEnableStatus(int enableStatus) {
        this.enableStatus = enableStatus;
    }

    //    @Title(title = "权重")
//    @Col(name = "weight")
    public Integer getWeight() {
        return weight;
    }

    public void setWeight(Integer weight) {
        this.weight = weight;
    }

    @Title(title = "是否用于应用")
    @Col(name = "used_app")
    public boolean getUsedApp() {
        return usedApp;
    }

    public void setUsedApp(boolean usedApp) {
        this.usedApp = usedApp;
    }
}
