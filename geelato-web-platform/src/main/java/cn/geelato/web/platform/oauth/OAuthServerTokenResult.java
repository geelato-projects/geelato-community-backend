package cn.geelato.web.platform.oauth;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OAuthServerTokenResult extends OAuthServerResult{
    private String token_type;
    private String access_token;
    private String refresh_token;
    private String scope;
    private long expires_in;
    private long refresh_expires_in;
    private String client_id;
}
