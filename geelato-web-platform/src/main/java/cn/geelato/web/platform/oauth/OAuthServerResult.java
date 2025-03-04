package cn.geelato.web.platform.oauth;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OAuthServerResult {
    private String code;
    private String msg;
    private String data;
}
