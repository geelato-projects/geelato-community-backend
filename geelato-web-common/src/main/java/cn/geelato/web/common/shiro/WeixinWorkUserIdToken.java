package cn.geelato.web.common.shiro;

import lombok.Getter;
import org.apache.shiro.authc.AuthenticationToken;

@Getter
public class WeixinWorkUserIdToken implements AuthenticationToken {
    private final String workUserId;

    public WeixinWorkUserIdToken(String workUserId) {
        this.workUserId = workUserId;
    }

    @Override
    public Object getPrincipal() {
        return workUserId;
    }

    @Override
    public Object getCredentials() {
        return workUserId;
    }
}
