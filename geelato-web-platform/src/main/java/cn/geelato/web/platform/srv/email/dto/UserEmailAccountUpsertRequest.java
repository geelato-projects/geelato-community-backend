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
    private Integer enableStatus;
}

