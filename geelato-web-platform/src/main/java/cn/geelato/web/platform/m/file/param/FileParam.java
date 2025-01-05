package cn.geelato.web.platform.m.file.param;

import cn.geelato.core.meta.annotation.Title;
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

    public FileParam(String serviceType, String sourceType, String objectId, String formIds, String genre, Date invalidTime, String batchNo, String appId, String tenantCode, Boolean thumbnail, Integer dimension, Double thumbScale) {
        super(objectId, formIds, genre, invalidTime, batchNo, appId, tenantCode, thumbnail, dimension, thumbScale);
        this.serviceType = serviceType;
        this.sourceType = sourceType;
    }

    public ThumbnailParam toThumbnailParam() {
        return new ThumbnailParam(this.getObjectId(), this.getFormIds(), this.getGenre(), this.getInvalidTime(), this.getBatchNo(), this.getAppId(), this.getTenantCode(), this.getThumbnail(), this.getDimension(), this.getThumbScale());
    }
}
