package cn.geelato.web.platform.m.base.entity;

import cn.geelato.core.enums.DeleteStatusEnum;
import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.annotation.Transient;
import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.web.oss.OSSFile;
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
    @Title(title = "对象id")
    @Col(name = "object_id")
    private String objectId;

    @Transient
    private String source;

    public Attach() {
    }

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

    public Attach(OSSFile ossFile, MultipartFile file) {
        setDelStatus(DeleteStatusEnum.NO.getCode());
        this.objectId = ossFile.getObjectId();
        this.path = ossFile.getObjectName();
        if (file != null) {
            this.name = file.getOriginalFilename();
            this.type = file.getContentType();
            this.size = file.getSize();
        } else {
            this.name = ossFile.getFileMeta().getFileName();
            this.type = ossFile.getFileMeta().getFileContentType();
            this.size = ossFile.getFileMeta().getFileSize();
        }
    }
}
