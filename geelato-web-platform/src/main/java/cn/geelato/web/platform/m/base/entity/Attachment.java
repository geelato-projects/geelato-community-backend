package cn.geelato.web.platform.m.base.entity;

import cn.geelato.core.enums.DeleteStatusEnum;
import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.annotation.Transient;
import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.utils.FileUtils;
import cn.geelato.web.oss.OSSFile;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@Getter
@Setter
public class Attachment extends BaseEntity {
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
    @Title(title = "分类")
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

    @Transient
    private String source;

    public Attachment() {
    }

    public Attachment(MultipartFile file) {
        setDelStatus(DeleteStatusEnum.NO.getCode());
        this.name = file.getOriginalFilename();
        this.type = file.getContentType();
        this.size = file.getSize();
    }

    public Attachment(File file) throws IOException {
        setDelStatus(DeleteStatusEnum.NO.getCode());
        this.name = file.getName();
        this.type = Files.probeContentType(file.toPath());
        this.size = file.length();
    }
}
