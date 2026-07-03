package cn.geelato.web.platform.srv.email.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class UserEmailAccountDto {
    private String id;
    private String emailAddress;
    private String displayName;
    private String providerCode;
    private Integer defaultFlag;
    private String imapHost;
    private Integer imapPort;
    private Integer imapSsl;
    private String imapFolderDefault;
    private String authType;
    private String authUser;
    private String smtpHost;
    private Integer smtpPort;
    private Integer smtpSsl;
    private Integer smtpStarttls;
    private String smtpAuthUser;
    private String smtpFromName;
    private String signatureHtml;
    private Integer enableStatus;
    private Integer syncEnabled;
    private Integer syncIntervalMinutes;
    private Date lastSyncAt;
    private String syncStatus;
    private Date createAt;
    private Date updateAt;
}
