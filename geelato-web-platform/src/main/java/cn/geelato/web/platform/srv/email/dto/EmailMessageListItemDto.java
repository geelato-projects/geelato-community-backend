package cn.geelato.web.platform.srv.email.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;

@Getter
@Setter
public class EmailMessageListItemDto {
    private String id;
    private String emailAccountId;
    private String folder;
    private String subject;
    private EmailAddressDto from;
    private List<EmailAddressDto> to;
    private List<EmailAddressDto> cc;
    private Date sentAt;
    private Date receivedAt;
    private Long size;
    private Boolean unread;
    private Boolean hasAttachments;
    private String snippet;
}

