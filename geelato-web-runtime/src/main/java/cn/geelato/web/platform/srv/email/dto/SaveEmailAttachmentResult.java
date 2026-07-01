package cn.geelato.web.platform.srv.email.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveEmailAttachmentResult {
    private String attachmentId;
    private String name;
    private Long size;
    private String contentType;
    private String downloadUrl;
}

