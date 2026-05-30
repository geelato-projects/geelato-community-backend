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
    private Integer defaultFlag;
    private String imapHost;
    private Integer imapPort;
    private Integer imapSsl;
    private String imapFolderDefault;
    private String authType;
    private String authUser;
    private Integer enableStatus;
    private Date createAt;
    private Date updateAt;
}

