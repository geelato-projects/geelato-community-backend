package cn.geelato.web.platform.oauth2;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OAuth2ServerResult {
    private String code;
    private String msg;
    private String data;
}
