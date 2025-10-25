package cn.geelato.web.platform.srv.file.param;

import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class FileParam extends ThumbnailParam {
    @Title(title = "归属服务器")
    private String serviceType;
    @Title(title = "归属模型")
    private String sourceType;

    public FileParam() {
    }

    public FileParam(String serviceType, String sourceType, String pid, String objectId, String formIds, String resolution, String genre, Date invalidTime, String batchNo, String appId, String tenantCode, Boolean thumbnail, Boolean onlyThumb, String dimension, String thumbScale) {
        super(pid, objectId, formIds, resolution, genre, invalidTime, batchNo, appId, tenantCode, thumbnail, onlyThumb, dimension, thumbScale);
        this.serviceType = serviceType;
        this.sourceType = sourceType;
    }

    public ThumbnailParam toThumbnailParam() {
        return new ThumbnailParam(this.getPid(), this.getObjectId(), this.getFormIds(), this.getResolution(), this.getGenre(), this.getInvalidTime(), this.getBatchNo(), this.getAppId(), this.getTenantCode(), this.isThumbnail(), this.isOnlyThumb(), this.getDimension(), this.getThumbScale());
    }

    public FileParam toFileParam() {
        return new FileParam(this.getServiceType(), this.getSourceType(), this.getPid(), this.getObjectId(), this.getFormIds(), this.getResolution(), this.getGenre(), this.getInvalidTime(), this.getBatchNo(), this.getAppId(), this.getTenantCode(), this.isThumbnail(), this.isOnlyThumb(), this.getDimension(), this.getThumbScale());
    }
}
