package cn.geelato.web.platform.m.base.entity;

import cn.geelato.core.enums.DeleteStatusEnum;
import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * @author geelato
 */
@Getter
@Setter
@Entity(name = "platform_resources", table = "platform_resources")
@Title(title = "资源信息", description = "对应各类资源文件，如mvel规则文件，sql语句等")
public class Resources extends BaseEntity {

    @Col(name = "app_id")
    @Title(title = "所属应用")
    private String appId;

    @Col(name = "name")
    @Title(title = "名称")
    private String name;

    @Col(name = "type")
    @Title(title = "类型")
    private String type;

    @Col(name = "genre")
    @Title(title = "类别")
    private String genre;

    @Col(name = "size")
    @Title(title = "大小")
    private Long size;

    @Col(name = "path")
    @Title(title = "绝对地址")
    private String path;

    @Col(name = "url")
    @Title(title = "相对地址")
    private String url;

    @Col(name = "object_id")
    @Title(title = "对象id")
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


}
