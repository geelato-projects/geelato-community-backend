package cn.geelato.web.common.shiro;

import lombok.Getter;
import org.apache.shiro.authc.AuthenticationToken;

/**
 * 外部系统固定令牌凭证（Authorization: SystemToken xxx）。
 * principal 固定为 system（不关联平台用户），credentials 为令牌值本身。
 */
@Getter
public class SystemTokenToken implements AuthenticationToken {
    private final String token;

    public SystemTokenToken(String token) {
        this.token = token;
    }

    @Override
    public Object getPrincipal() {
        return "system";
    }

    @Override
    public Object getCredentials() {
        return token;
    }
}
