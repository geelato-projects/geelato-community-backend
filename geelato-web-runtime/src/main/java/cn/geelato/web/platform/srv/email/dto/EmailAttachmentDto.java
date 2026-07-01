package cn.geelato.web.platform.srv.email.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmailAttachmentDto {
    private String partId;
    private String fileName;
    private String contentType;
    private Long size;
    private Boolean inline;
    private String contentId;
    private String downloadUrl;
    private String saveToOssUrl;
}

