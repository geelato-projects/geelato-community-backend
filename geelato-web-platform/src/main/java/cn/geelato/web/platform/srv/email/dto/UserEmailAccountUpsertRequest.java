package cn.geelato.web.platform.srv.email.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserEmailAccountUpsertRequest {
    private String id;
    private String emailAddress;
    private String displayName;
    private Integer isDefault;
    private String imapHost;
    private Integer imapPort;
    private Integer imapSsl;
    private String imapFolderDefault;
    private String authType;
    private String authUser;
    private String authSecret;
    private Integer enableStatus;
}

