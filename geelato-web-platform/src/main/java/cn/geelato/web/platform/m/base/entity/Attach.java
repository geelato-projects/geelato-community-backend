package cn.geelato.web.platform.m.base.entity;

import cn.geelato.core.enums.DeleteStatusEnum;
import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.annotation.Transient;
import cn.geelato.core.meta.model.entity.BaseEntity;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * @author diabl
 */
@Getter
@Setter
@Entity(name = "platform_attach")
@Title(title = "附件")
@Accessors(chain = true)
public class Attach extends BaseEntity {

    @Col(name = "app_id")
    @Title(title = "所属应用")
    private String appId;
    private String name;
    private String type;
    private String genre;
    private Long size;
    private String path;
    private String url;

    @Col(name = "object_id")
    @Title(title = "对象id")
    private String objectId;

    @Transient
    private String source;


    public Attach(MultipartFile file) {
        setDelStatus(DeleteStatusEnum.NO.getCode());
        this.name = file.getOriginalFilename();
        this.type = file.getContentType();
        this.size = file.getSize();
    }

    public Attach(File file) throws IOException {
        setDelStatus(DeleteStatusEnum.NO.getCode());
        this.name = file.getName();
        this.type = Files.probeContentType(file.toPath());
        this.size = file.length();
    }


    public Attach() {

    }
}
