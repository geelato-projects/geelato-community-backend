package cn.geelato.web.platform.srv.email.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmailContactUpsertRequest {
    private String id;
    private String emailAccountId;
    private String name;
    private String emailAddress;
    private String companyName;
    private String remark;
    private String tagsJson;
    private Integer favoriteFlag;
    private String sourceType;
    private Integer enableStatus;
}
