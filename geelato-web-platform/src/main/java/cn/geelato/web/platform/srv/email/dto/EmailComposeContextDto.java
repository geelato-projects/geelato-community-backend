package cn.geelato.web.platform.srv.email.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class EmailComposeContextDto {
    private String sourceMailId;
    private String composeMode;
    private String subject;
    private List<EmailAddressDto> to;
    private List<EmailAddressDto> cc;
    private String inReplyToMessageId;
    private String referencesHeader;
    private String quotedText;
    private String quotedHtml;
}
