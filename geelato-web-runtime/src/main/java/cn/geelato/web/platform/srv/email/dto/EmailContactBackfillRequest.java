package cn.geelato.web.platform.srv.email.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class EmailContactBackfillRequest {
    private String emailAccountId;
    private List<String> folders;
    private Integer messageLimit;
    private Boolean includeTo;
    private Boolean includeCc;
    private Boolean includeFrom;
}
