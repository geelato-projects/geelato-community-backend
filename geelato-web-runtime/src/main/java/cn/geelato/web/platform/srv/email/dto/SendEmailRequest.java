package cn.geelato.web.platform.srv.email.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SendEmailRequest {
    private String emailAccountId;
    private String fromName;
    private List<EmailAddressDto> to;
    private List<EmailAddressDto> cc;
    private List<EmailAddressDto> bcc;
    private String subject;
    private String textBody;
    private String htmlBody;
    private List<String> attachmentIds;
    private List<MailAttachmentRefDto> mailAttachmentRefs;
    private String composeMode;
    private String sourceMailId;
    private String inReplyToMessageId;
    private String referencesHeader;
    private Boolean saveAsDraftOnFail;
    private String draftId;
}
