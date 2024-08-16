package cn.geelato.web.platform.m.syspackage.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;

import java.util.Date;

@Entity(name = "platform_app_version", table = "platform_app_version")
@Title(title = "应用版本")
public class AppVersion extends BaseEntity {
    private String packagePath;
    private String version;
    private String packageSource;
    private String appId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date packetTime;
    private String status;
    private String description;

    @Col(name = "package_path")
    @Title(title = "package_path")
    public String getPackagePath() {
        return packagePath;
    }

    public void setPackagePath(String packagePath) {
        this.packagePath = packagePath;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    @Col(name = "package_source")
    @Title(title = "package_source")
    public String getPackageSource() {
        return packageSource;
    }

    public void setPackageSource(String packageSource) {
        this.packageSource = packageSource;
    }

    @Col(name = "app_id")
    @Title(title = "app_id")
    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    @Col(name = "packet_time")
    @Title(title = "packet_time")
    public Date getPacketTime() {
        return packetTime;
    }

    public void setPacketTime(Date packetTime) {
        this.packetTime = packetTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
