package cn.geelato.web.platform.shiro;

import lombok.Getter;
import lombok.Setter;
import org.apache.shiro.authc.AuthenticationToken;

public class OAuth2Token implements AuthenticationToken {
    private String accessToken;
    public OAuth2Token(String accessToken) {
        this.accessToken = accessToken;
    }
    @Override
    public Object getPrincipal() {
        return accessToken;
    }

    @Override
    public Object getCredentials() {
        return accessToken;
    }

    public String getAccessToken() {
        return accessToken;
    }
}
