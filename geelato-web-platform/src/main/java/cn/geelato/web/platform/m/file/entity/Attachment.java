package cn.geelato.web.platform.m.file.entity;

import cn.geelato.core.enums.DeleteStatusEnum;
import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.annotation.Transient;
import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.utils.DateUtils;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;
import org.springframework.lang.Nullable;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

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
        // 1. 初始化去重集合（保持顺序）
        Set<String> genres = handleGenre(this.genre);
        // 3. 处理传入参数
        if (args != null) {
            Arrays.stream(args)
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(genres::add);
        }
        // 4. 设置结果
        this.genre = handleGenre(genres);
    }

    public void handleGenre() {
        // 1. 初始化去重集合（保持顺序）
        Set<String> genres = handleGenre(this.genre);
        // 2. 设置结果
        this.genre = handleGenre(genres);
    }

    private Set<String> handleGenre(String genre) {
        // 1. 初始化去重集合（保持顺序）
        Set<String> genres = new LinkedHashSet<>();
        // 2. 处理现有genre
        if (genre != null && !genre.trim().isEmpty()) {
            Collections.addAll(genres, genre.split("\\s*,\\s*"));
        }
        return genres;
    }

    private String handleGenre(Set<String> genres) {
        return genres.isEmpty() ? null : String.join(",", genres);
    }
}
