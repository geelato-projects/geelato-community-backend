package cn.geelato.web.common.shiro;

import lombok.Getter;
import org.apache.shiro.authc.AuthenticationToken;

@Getter
public class WeixinUnionIdToken implements AuthenticationToken {
    private final String unionId;

    public WeixinUnionIdToken(String unionId) {
        this.unionId = unionId;
    }

    @Override
    public Object getPrincipal() {
        return unionId;
    }

    @Override
    public Object getCredentials() {
        return unionId;
    }
}
