package cn.geelato.web.platform.m.file.param;

import cn.geelato.core.meta.annotation.Title;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class ThumbnailParam extends AttachmentParam {
    @Title(title = "缩略图")
    private Boolean thumbnail;
    @Title(title = "缩略图尺寸")
    private Integer dimension;
    @Title(title = "缩略图尺寸比例")
    private Double thumbScale;

    public ThumbnailParam() {
    }

    public ThumbnailParam(boolean thumbnail, Integer dimension, Double thumbScale) {
        this.thumbnail = thumbnail;
        this.dimension = dimension;
        this.thumbScale = thumbScale;
    }

    public ThumbnailParam(String objectId, String formIds, String genre, Date invalidTime, Boolean thumbnail, Integer dimension, Double thumbScale) {
        super(objectId, formIds, genre, invalidTime);
        this.thumbnail = thumbnail;
        this.dimension = dimension;
        this.thumbScale = thumbScale;
    }

    public ThumbnailParam(String objectId, String formIds, String genre, Date invalidTime, String appId, String tenantCode, Boolean thumbnail, Integer dimension, Double thumbScale) {
        super(objectId, formIds, genre, invalidTime, appId, tenantCode);
        this.thumbnail = thumbnail;
        this.dimension = dimension;
        this.thumbScale = thumbScale;
    }

    public AttachmentParam toAttachmentParam() {
        return new AttachmentParam(getObjectId(), getFormIds(), getGenre(), getInvalidTime(), getAppId(), getTenantCode());
    }

    public boolean isThumbnail() {
        return this.getThumbnail() == null ? false : this.getThumbnail().booleanValue();
    }
}
