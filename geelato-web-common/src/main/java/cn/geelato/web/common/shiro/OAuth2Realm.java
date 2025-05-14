package cn.geelato.web.common.shiro;

import cn.geelato.web.common.interceptor.OAuthConfigurationProperties;
import cn.geelato.web.common.oauth2.OAuth2Helper;
import cn.geelato.web.common.security.User;
import lombok.SneakyThrows;
import org.apache.shiro.authc.*;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class OAuth2Realm extends AuthorizingRealm {

    @Autowired
    private OAuthConfigurationProperties oAuthConfigurationProperties;

    @Override
    public boolean supports(AuthenticationToken token) {
        return token instanceof OAuth2Token;
    }
    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principalCollection) {
        ShiroUser shiroUser = (ShiroUser) principalCollection.getPrimaryPrincipal();
        Map params = new HashMap(1);
        params.put("loginName", shiroUser.loginName);
        return new SimpleAuthorizationInfo();
    }

    @SneakyThrows
    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken authenticationToken) throws AuthenticationException {
        OAuth2Token oauth2Token = (OAuth2Token) authenticationToken;
        String accessToken = oauth2Token.getAccessToken();
        User user = getUserInfo(accessToken);
        if (user != null) {
            return new SimpleAuthenticationInfo(
                    new ShiroUser(user.getId(), user.getLoginName(), user.getName()),
                    accessToken,
                    getName()
            );
        } else {
            return null;
        }
    }

    private User getUserInfo(String accessToken) throws IOException {
        return OAuth2Helper.getUserInfo(oAuthConfigurationProperties.getUrl(),accessToken);
    }
}
