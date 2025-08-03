package cn.geelato.core.meta.model.connect;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.core.meta.model.entity.EntityEnableAble;
import lombok.Getter;
import lombok.Setter;

/**
 * @author lynch.liu
 */
@Getter
@Setter
@Title(title = "数据库连接信息")
@Entity(name = "platform_dev_db_connect",catalog = "platform")
public class ConnectMeta extends BaseEntity implements EntityEnableAble {
    @Title(title = "应用Id")
    @Col(name = "app_id")
    private String appId;
    @Col(name = "db_connect_name", nullable = false, charMaxlength = 255)
    @Title(title = "连接名称", description = "连接名称")
    private String dbConnectName;
    @Col(name = "db_name", nullable = false, charMaxlength = 150)
    @Title(title = "数据库名", description = "数据库名")
    private String dbName;
    @Col(name = "db_schema", nullable = false, charMaxlength = 255)
    @Title(title = "数据库schema", description = "数据库schema")
    private String dbSchema;
    @Col(name = "db_type", nullable = false, charMaxlength = 20)
    @Title(title = "数据库类型", description = "数据来自字典")
    private String dbType;
    @Col(name = "db_user_name", nullable = false, charMaxlength = 20)
    @Title(title = "用户名", description = "用户名")
    private String dbUserName;
    @Col(name = "db_password", nullable = false, charMaxlength = 50)
    @Title(title = "密码", description = "密码")
    private String dbPassword;
    @Col(name = "db_port")
    @Title(title = "连接端口", description = "连接端口")
    private int dbPort;
    @Col(name = "db_hostname_ip", nullable = false, charMaxlength = 150)
    @Title(title = "主机名或IP", description = "主机名或IP")
    private String dbHostnameIp;
    @Title(title = "启用状态", description = "1表示启用、0表示未启用")
    @Col(name = "enable_status", nullable = false, numericPrecision = 1)
    private int enableStatus = ColumnDefault.ENABLE_STATUS_VALUE;
}
