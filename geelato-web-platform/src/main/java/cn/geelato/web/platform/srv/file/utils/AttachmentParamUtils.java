package cn.geelato.web.platform.srv.file.utils;

import cn.geelato.web.platform.srv.file.param.AttachmentParam;

public class AttachmentParamUtils {
    /**
     * 构建附件参数，用于生成缩略图
     *
     * @param genre      文件类型
     * @param appId      应用ID
     * @param tenantCode 租户代码
     * @return 构建好的附件参数对象
     */
    public static AttachmentParam byThumbnail(String genre, String appId, String tenantCode) {
        return new AttachmentParam(null, null, null, null, genre, null, null, appId, tenantCode);
    }
}
