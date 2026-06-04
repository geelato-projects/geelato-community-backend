package cn.geelato.web.platform.srv.email.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserEmailAccountUpsertRequest {
    private String id;
    private String emailAddress;
    private String displayName;
    private String providerCode;
    @JsonAlias({"isDefault", "default", "default_flag"})
    private Integer defaultFlag;
    private String imapHost;
    private Integer imapPort;
    private Integer imapSsl;
    private String imapFolderDefault;
    private String authType;
    private String authUser;
    private String authSecret;
    private String oauth2Json;
    private String smtpHost;
    private Integer smtpPort;
    private Integer smtpSsl;
    private Integer smtpStarttls;
    private String smtpAuthUser;
    private String smtpAuthSecret;
    private String smtpFromName;
    private String signatureHtml;
    private Integer enableStatus;
}

