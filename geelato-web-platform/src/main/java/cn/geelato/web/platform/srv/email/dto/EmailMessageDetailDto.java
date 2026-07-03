package cn.geelato.web.platform.srv.email.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;

@Getter
@Setter
public class EmailMessageDetailDto {
    private String id;
    private String emailAccountId;
    private String folder;
    private String messageId;
    private String subject;
    private EmailAddressDto from;
    private List<EmailAddressDto> to;
    private List<EmailAddressDto> cc;
    private List<EmailAddressDto> bcc;
    private Date sentAt;
    private Date receivedAt;
    private String textBody;
    private String htmlBody;
    private List<EmailAttachmentDto> attachments;
}

