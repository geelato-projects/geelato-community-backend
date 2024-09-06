package cn.geelato.web.platform.m.syspackage.entity;

import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.utils.DateUtils;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Setter;

import java.util.Date;

/**
 * @author diabl
 */
@Setter
@Entity(name = "platform_app_version", table = "platform_app_version")
@Title(title = "应用版本")
public class AppVersion extends BaseEntity {
    private String packagePath;
    private String version;
    private String packageSource;
    private String appId;
    @JsonFormat(pattern = DateUtils.DATETIME, timezone = DateUtils.TIMEZONE)
    private Date packetTime;
    private String status;
    private String description;

    @Col(name = "package_path")
    @Title(title = "package_path")
    public String getPackagePath() {
        return packagePath;
    }

    @Col(name = "version")
    @Title(title = "version")
    public String getVersion() {
        return version;
    }

    @Col(name = "package_source")
    @Title(title = "package_source")
    public String getPackageSource() {
        return packageSource;
    }

    @Col(name = "app_id")
    @Title(title = "app_id")
    public String getAppId() {
        return appId;
    }

    @Col(name = "packet_time")
    @Title(title = "packet_time")
    public Date getPacketTime() {
        return packetTime;
    }

    @Col(name = "status")
    @Title(title = "status")
    public String getStatus() {
        return status;
    }

    @Col(name = "description")
    @Title(title = "description")
    public String getDescription() {
        return description;
    }
}
