package cn.geelato.web.platform.srv.email.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveEmailAttachmentRequest {
    private String serviceType;
    private String sourceType;
    private String objectId;
    private String appId;
    private String tenantCode;
}

