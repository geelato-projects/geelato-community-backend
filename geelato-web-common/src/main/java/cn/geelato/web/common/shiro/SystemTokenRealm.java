package cn.geelato.web.common.shiro;

import cn.geelato.web.common.interceptor.SystemTokenProperties;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;

/**
 * 外部系统固定令牌 Realm：仅校验凭证值与配置密钥一致，
 * 不关联平台用户、不授予任何角色/权限。
 */
public class SystemTokenRealm extends AuthorizingRealm {

    private final SystemTokenProperties properties;

    public SystemTokenRealm(SystemTokenProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean supports(AuthenticationToken token) {
        return token instanceof SystemTokenToken;
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        return new SimpleAuthorizationInfo();
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken authToken) throws AuthenticationException {
        SystemTokenToken token = (SystemTokenToken) authToken;
        String provided = token.getCredentials() == null ? null : String.valueOf(token.getCredentials());
        if (properties == null || !properties.matches(provided)) {
            return null;
        }
        return new SimpleAuthenticationInfo(token.getPrincipal(), token.getCredentials(), getName());
    }
}
