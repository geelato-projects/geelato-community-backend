package cn.geelato.web.platform.m.file.entity;

import cn.geelato.core.enums.DeleteStatusEnum;
import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.annotation.Transient;
import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.utils.DateUtils;
import cn.geelato.utils.StringUtils;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;
import org.springframework.lang.Nullable;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Date;
import java.util.List;

@Getter
@Setter
public class Attachment extends BaseEntity {
    @Title(title = "父id")
    private String pid;
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
    @Col(name = "object_id")
    @Title(title = "对象id")
    private String objectId;
    @Col(name = "form_ids")
    @Title(title = "表单id")
    private String formIds;
    @Col(name = "invalid_time")
    @Title(title = "失效时间")
    @JsonFormat(pattern = DateUtils.DATETIME, timezone = DateUtils.TIMEZONE)
    private Date invalidTime;
    @Col(name = "batch_no")
    @Title(title = "批次号")
    private String batchNo;
    @Title(title = "分辨率", description = "像素,单位:px")
    private String resolution;

    @Transient
    private String source;
    @Transient
    private String storageType;

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

    public void handleGenre(@Nullable Object... args) {
        List<String> genres = StringUtils.toListDr(this.genre);
        if (args != null && args.length > 0) {
            for (Object arg : args) {
                if (StringUtils.isNotBlank(arg.toString())) {
                    genres.add(arg.toString());
                }
            }
        }
        this.genre = genres.size() > 0 ? StringUtils.join(genres, ",") : null;
    }
}
