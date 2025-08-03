package cn.geelato.web.platform.m.syspackage.entity;

import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.utils.DateUtils;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * @author diabl
 */
@Getter
@Setter
@Entity(name = "platform_app_version")
@Title(title = "应用版本")
public class AppVersion extends BaseEntity {
    @Title(title = "包地址")
    @Col(name = "package_path")
    private String packagePath;
    @Title(title = "版本名称")
    private String version;
    @Title(title = "包来源")
    @Col(name = "package_source")
    private String packageSource;
    @Title(title = "所属应用")
    @Col(name = "app_id")
    private String appId;
    @Title(title = "打包时间")
    @JsonFormat(pattern = DateUtils.DATETIME, timezone = DateUtils.TIMEZONE)
    @Col(name = "packet_time")
    private Date packetTime;
    @Title(title = "状态")
    private String status;
    @Title(title = "描述")
    private String description;
}
