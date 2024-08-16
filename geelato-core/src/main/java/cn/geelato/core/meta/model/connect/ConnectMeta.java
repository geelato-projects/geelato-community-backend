package cn.geelato.core.meta.model.connect;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.annotation.Transient;
import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.core.meta.model.entity.EntityEnableAble;
import lombok.Setter;

/**
 * @author lynch.liu
 */
@Setter
@Title(title = "数据库连接信息")
@Entity(name = "platform_dev_db_connect")
public class ConnectMeta extends BaseEntity implements EntityEnableAble {

    private String appId;
    private String dbConnectName;
    private String dbName;
    private String dbSchema;
    private String dbType;
    private String dbUserName;
    private String dbPassword;
    private int dbPort;
    private String dbHostnameIp;
    private int enableStatus = ColumnDefault.ENABLE_STATUS_VALUE;
    private String apps;

    @Title(title = "应用Id")
    @Col(name = "app_id")
    public String getAppId() {
        return appId;
    }

    @Col(name = "db_connect_name", nullable = false, charMaxlength = 255)
    @Title(title = "连接名称", description = "连接名称")
    public String getDbConnectName() {
        return dbConnectName;
    }

    @Col(name = "db_name", nullable = false, charMaxlength = 150)
    @Title(title = "数据库名", description = "数据库名")
    public String getDbName() {
        return dbName;
    }

    @Col(name = "db_schema", nullable = false, charMaxlength = 255)
    @Title(title = "数据库schema", description = "数据库schema")
    public String getDbSchema() {
        return dbSchema;
    }

    @Col(name = "db_type", nullable = false, charMaxlength = 20)
    @Title(title = "数据库类型", description = "数据来自字典")
    public String getDbType() {
        return dbType;
    }

    @Col(name = "db_user_name", nullable = false, charMaxlength = 20)
    @Title(title = "用户名", description = "用户名")
    public String getDbUserName() {
        return dbUserName;
    }

    @Col(name = "db_password", nullable = false, charMaxlength = 50)
    @Title(title = "密码", description = "密码")
    public String getDbPassword() {
        return dbPassword;
    }

    @Col(name = "db_port")
    @Title(title = "连接端口", description = "连接端口")
    public int getDbPort() {
        return dbPort;
    }

    @Col(name = "db_hostname_ip", nullable = false, charMaxlength = 150)
    @Title(title = "主机名或IP", description = "主机名或IP")
    public String getDbHostnameIp() {
        return dbHostnameIp;
    }

    @Title(title = "启用状态", description = "1表示启用、0表示未启用")
    @Col(name = "enable_status", nullable = false, numericPrecision = 1)
    @Override
    public int getEnableStatus() {
        return this.enableStatus;
    }

    @Transient
    public String getApps() {
        return apps;
    }
}
