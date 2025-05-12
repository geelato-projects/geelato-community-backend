package cn.geelato.web.common.oauth2;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OAuth2ServerTokenResult extends OAuth2ServerResult {
    private String token_type;
    private String access_token;
    private String refresh_token;
    private String scope;
    private long expires_in;
    private long refresh_expires_in;
    private String client_id;
}
