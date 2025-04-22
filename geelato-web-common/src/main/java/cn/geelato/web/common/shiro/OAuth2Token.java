package cn.geelato.web.common.shiro;

import lombok.Getter;
import lombok.Setter;
import org.apache.shiro.authc.AuthenticationToken;

@Getter
public class OAuth2Token implements AuthenticationToken {
    private final String accessToken;
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

}
