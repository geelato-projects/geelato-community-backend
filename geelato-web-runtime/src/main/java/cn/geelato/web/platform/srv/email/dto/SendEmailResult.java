package cn.geelato.web.platform.srv.email.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class SendEmailResult {
    private String draftId;
    private String messageId;
    private Date sentAt;
    private Boolean contactSynced;
}
