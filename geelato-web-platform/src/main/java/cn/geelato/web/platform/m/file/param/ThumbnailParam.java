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

    public ThumbnailParam(String objectId, String formIds, String genre, Date invalidTime, String batchNo, String appId, String tenantCode, Boolean thumbnail, Integer dimension, Double thumbScale) {
        super(objectId, formIds, genre, invalidTime, batchNo, appId, tenantCode);
        this.thumbnail = thumbnail;
        this.dimension = dimension;
        this.thumbScale = thumbScale;
    }

    public AttachmentParam toAttachmentParam() {
        return new AttachmentParam(this.getObjectId(), this.getFormIds(), this.getGenre(), this.getInvalidTime(), this.getBatchNo(), this.getAppId(), this.getTenantCode());
    }

    public boolean isThumbnail() {
        return this.getThumbnail() != null && this.getThumbnail().booleanValue();
    }
}
