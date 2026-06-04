package cn.geelato.web.platform.srv.email.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;

@Getter
@Setter
public class EmailDraftDto {
    private String id;
    private String emailAccountId;
    private String fromName;
    private String subject;
    private List<EmailAddressDto> to;
    private List<EmailAddressDto> cc;
    private List<EmailAddressDto> bcc;
    private String bodyType;
    private String textBody;
    private String htmlBody;
    private List<String> attachmentIds;
    private List<MailAttachmentRefDto> mailAttachmentRefs;
    private String sourceMailId;
    private String composeMode;
    private String inReplyToMessageId;
    private String referencesHeader;
    private Date autoSaveAt;
    private String sendStatus;
    private Integer enableStatus;
    private Date createAt;
    private Date updateAt;
}
