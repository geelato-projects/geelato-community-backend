package cn.geelato.web.platform.m.base.entity;

import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.ForeignKey;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.connect.ConnectMeta;
import cn.geelato.core.meta.model.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * @author diabl
 * @description: 应用数据链接关系表
 */
@Getter
@Setter
@Entity(name = "platform_app_r_connect")
@Title(title = "应用数据链接关系表")
public class AppConnectMap extends BaseEntity {
    @Title(title = "应用ID")
    @ForeignKey(fTable = App.class)
    @Col(name = "app_id", refTables = "platform_app", refColName = "platform_app.id")
    private String appId;
    @Title(title = "应用名称")
    @Col(name = "app_name", isRefColumn = true, refLocalCol = "appId", refColName = "platform_app.name")
    private String appName;
    @Title(title = "数据链接ID")
    @ForeignKey(fTable = ConnectMeta.class)
    @Col(name = "connect_id", refTables = "platform_dev_db_connect", refColName = "platform_dev_db_connect.id")
    private String connectId;
    @Title(title = "数据链接")
    @Col(name = "connect_name", refTables = "platform_dev_db_connect", refColName = "platform_dev_db_connect.dbConnectName")
    private String connectName;
}
