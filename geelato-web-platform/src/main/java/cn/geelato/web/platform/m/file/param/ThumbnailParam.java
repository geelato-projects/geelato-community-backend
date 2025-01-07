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
    @Title(title = "仅保存缩略图")
    private Boolean onlyThumb;
    @Title(title = "缩略图尺寸")
    private String dimension;
    @Title(title = "缩略图尺寸比例")
    private String thumbScale;

    public ThumbnailParam() {
    }

    public ThumbnailParam(String pid, String objectId, String formIds, String resolution, String genre, Date invalidTime, String batchNo, String appId, String tenantCode, Boolean thumbnail, Boolean onlyThumb, String dimension, String thumbScale) {
        super(pid, objectId, formIds, resolution, genre, invalidTime, batchNo, appId, tenantCode);
        this.thumbnail = thumbnail;
        this.onlyThumb = onlyThumb;
        this.dimension = dimension;
        this.thumbScale = thumbScale;
    }

    public AttachmentParam toAttachmentParam() {
        return new AttachmentParam(this.getPid(), this.getObjectId(), this.getFormIds(), this.getResolution(), this.getGenre(), this.getInvalidTime(), this.getBatchNo(), this.getAppId(), this.getTenantCode());
    }

    public boolean isThumbnail() {
        return this.getThumbnail() != null && this.getThumbnail().booleanValue();
    }

    public boolean isOnlyThumb() {
        return this.getOnlyThumb() != null && this.getOnlyThumb().booleanValue();
    }
}
