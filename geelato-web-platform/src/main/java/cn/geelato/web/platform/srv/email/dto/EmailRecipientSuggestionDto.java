package cn.geelato.web.platform.srv.email.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class EmailRecipientSuggestionDto {
    private String id;
    private String name;
    private String emailAddress;
    private Integer favoriteFlag;
    private Date lastContactAt;
    private String sourceType;
}
