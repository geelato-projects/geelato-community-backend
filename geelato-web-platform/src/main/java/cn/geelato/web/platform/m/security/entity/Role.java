package cn.geelato.web.platform.m.security.entity;


import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import cn.geelato.lang.meta.Transient;
import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import cn.geelato.core.meta.model.entity.EntityEnableAble;
import lombok.Getter;
import lombok.Setter;

/**
 * Created by hongxueqian on 14-4-12.
 */
@Getter
@Setter
@Entity(name = "platform_role")
@Title(title = "角色")
public class Role extends BaseSortableEntity implements EntityEnableAble {
    @Title(title = "应用")
    @Col(name = "app_id")
    private String appId;
    @Title(title = "名称")
    private String name;
    @Title(title = "编码")
    private String code;
    @Title(title = "类型", description = "app | platform，即应用级角色、平台级角色")
    private String type;
    @Title(title = "启用状态", description = "1表示启用、0表示未启用")
    @Col(name = "enable_status", nullable = false, dataType = "tinyint", numericPrecision = 2)
    private int enableStatus = ColumnDefault.ENABLE_STATUS_VALUE;
    @Title(title = "描述")
    private String description;
    @Title(title = "权重")
    private Integer weight;
    @Title(title = "是否用于应用")
    @Col(name = "used_app")
    private int usedApp;

    @Transient
    private String appName;
    @Transient
    private String appIds;
    @Transient
    public boolean isUsedApp() {
        return usedApp > 0;
    }
}
