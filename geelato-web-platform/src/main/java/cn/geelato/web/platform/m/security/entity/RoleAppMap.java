package cn.geelato.web.platform.m.security.entity;


import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.ForeignKey;
import cn.geelato.lang.meta.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.web.platform.m.base.entity.App;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity(name = "platform_role_r_app")
@Title(title = "角色APP关系表")
public class RoleAppMap extends BaseEntity {
    @Title(title = "角色ID")
    @ForeignKey(fTable = Role.class)
    @Col(name = "role_id", refTables = "platform_role", refColName = "platform_role.id")
    private String roleId;
    @Title(title = "角色名称")
    @Col(name = "role_name", isRefColumn = true, refLocalCol = "roleId", refColName = "platform_role.name")
    private String roleName;
    @Title(title = "应用ID")
    @ForeignKey(fTable = App.class)
    @Col(name = "app_id", refTables = "platform_app", refColName = "platform_app.id")
    private String appId;
    @Title(title = "应用名称")
    @Col(name = "app_name", isRefColumn = true, refLocalCol = "appId", refColName = "platform_app.name")
    private String appName;
}
