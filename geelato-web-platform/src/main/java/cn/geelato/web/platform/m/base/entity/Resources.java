package cn.geelato.web.platform.m.base.entity;

import cn.geelato.core.enums.DeleteStatusEnum;
import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * @author geelato
 */
@Setter
@Entity(name = "platform_resources", table = "platform_resources")
@Title(title = "资源信息", description = "对应各类资源文件，如mvel规则文件，sql语句等")
public class Resources extends BaseEntity {

    private String appId;
    private String name;
    private String type;
    private String genre;
    private Long size;
    private String path;
    private String url;
    private String objectId;

    public Resources() {
    }

    public Resources(MultipartFile file) {
        setDelStatus(DeleteStatusEnum.NO.getCode());
        this.name = file.getOriginalFilename();
        this.type = file.getContentType();
        this.size = file.getSize();
    }

    public Resources(File file) throws IOException {
        setDelStatus(DeleteStatusEnum.NO.getCode());
        this.name = file.getName();
        this.type = Files.probeContentType(file.toPath());
        this.size = file.length();
    }

    @Col(name = "app_id")
    @Title(title = "所属应用")
    public String getAppId() {
        return appId;
    }

    @Col(name = "name")
    @Title(title = "名称")
    public String getName() {
        return name;
    }

    @Col(name = "type")
    @Title(title = "类型")
    public String getType() {
        return type;
    }

    @Col(name = "genre")
    @Title(title = "类别")
    public String getGenre() {
        return genre;
    }

    @Col(name = "size")
    @Title(title = "大小")
    public Long getSize() {
        return size;
    }

    @Col(name = "path")
    @Title(title = "绝对地址")
    public String getPath() {
        return path;
    }

    @Col(name = "url")
    @Title(title = "相对地址")
    public String getUrl() {
        return url;
    }

    @Col(name = "object_id")
    @Title(title = "对象id")
    public String getObjectId() {
        return objectId;
    }
}
