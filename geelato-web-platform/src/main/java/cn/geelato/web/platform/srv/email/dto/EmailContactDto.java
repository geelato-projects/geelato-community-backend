package cn.geelato.web.platform.srv.email.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class EmailContactDto {
    private String id;
    private String emailAccountId;
    private String name;
    private String emailAddress;
    private String companyName;
    private String remark;
    private String tagsJson;
    private Integer favoriteFlag;
    private String sourceType;
    private Date lastSentAt;
    private Date lastReceivedAt;
    private Date lastContactAt;
    private Integer contactCount;
    private Integer enableStatus;
    private Date createAt;
    private Date updateAt;
}
