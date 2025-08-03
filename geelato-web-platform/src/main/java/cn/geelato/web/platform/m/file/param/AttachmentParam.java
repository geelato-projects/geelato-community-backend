package cn.geelato.web.platform.m.file.param;

import cn.geelato.lang.meta.Title;
import cn.geelato.web.platform.m.file.entity.Attachment;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class AttachmentParam {
    @Title(title = "父id")
    private String pid;
    @Title(title = "云服务器判断信息")
    private String objectId;
    @Title(title = "组成该压缩文件的文件id")
    private String formIds;
    @Title(title = "文件分类，例如：图片、视频等")
    private String genre;
    @Title(title = "失效时间")
    private Date invalidTime;
    @Title(title = "批次号")
    private String batchNo;
    @Title(title = "分辨率", description = "像素,单位:px")
    private String resolution;

    @Title(title = "归属应用")
    private String appId;
    @Title(title = "归属租户")
    private String tenantCode;

    public AttachmentParam() {
    }

    public AttachmentParam(String pid, String objectId, String formIds, String resolution, String genre, Date invalidTime, String batchNo, String appId, String tenantCode) {
        this.pid = pid;
        this.objectId = objectId;
        this.formIds = formIds;
        this.resolution = resolution;
        this.genre = genre;
        this.invalidTime = invalidTime;
        this.batchNo = batchNo;
        this.appId = appId;
        this.tenantCode = tenantCode;
    }

    public <T extends Attachment> T toAttachment(T attachment) {
        attachment.setPid(this.getPid());
        attachment.setObjectId(this.getObjectId());
        attachment.setFormIds(this.getFormIds());
        attachment.setResolution(this.getResolution());
        attachment.setGenre(this.getGenre());
        attachment.setInvalidTime(this.getInvalidTime());
        attachment.setBatchNo(this.getBatchNo());
        attachment.setAppId(this.getAppId());
        attachment.setTenantCode(this.getTenantCode());
        attachment.handleGenre();
        return attachment;
    }
}
