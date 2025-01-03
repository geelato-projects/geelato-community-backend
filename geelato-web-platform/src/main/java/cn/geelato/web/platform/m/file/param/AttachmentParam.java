package cn.geelato.web.platform.m.file.param;

import cn.geelato.core.meta.annotation.Title;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class AttachmentParam {
    @Title(title = "云服务器判断信息")
    private String objectId;
    @Title(title = "组成该压缩文件的文件id")
    private String formIds;
    @Title(title = "文件分类，例如：图片、视频等")
    private String genre;
    @Title(title = "失效时间")
    private Date invalidTime;

    @Title(title = "归属应用")
    private String appId;
    @Title(title = "归属租户")
    private String tenantCode;

    public AttachmentParam() {
    }

    public AttachmentParam(String objectId, String formIds, String genre, Date invalidTime) {
        this.objectId = objectId;
        this.formIds = formIds;
        this.genre = genre;
        this.invalidTime = invalidTime;
    }

    public AttachmentParam(String objectId, String formIds, String genre, Date invalidTime, String appId, String tenantCode) {
        this.objectId = objectId;
        this.formIds = formIds;
        this.genre = genre;
        this.invalidTime = invalidTime;
        this.appId = appId;
        this.tenantCode = tenantCode;
    }
}
