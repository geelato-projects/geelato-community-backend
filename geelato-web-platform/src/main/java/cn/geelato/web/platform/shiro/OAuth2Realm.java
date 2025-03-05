package cn.geelato.web.platform.shiro;

import cn.geelato.core.orm.Dao;
import cn.geelato.utils.Encodes;
import cn.geelato.web.platform.boot.properties.OAuthConfigurationProperties;
import cn.geelato.web.platform.m.security.entity.User;
import cn.geelato.web.platform.oauth.OAuthHelper;
import lombok.SneakyThrows;
import org.apache.shiro.authc.*;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.util.ByteSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class OAuth2Realm extends AuthorizingRealm {

    @Autowired
    @Qualifier("primaryDao")
    protected Dao dao;
    private final static String SECURITY_USER_PERMISSION_STRING_LIST = "security_user_permission_string_list";
    private final static String SECURITY_USER_ROLE_CODE_LIST = "security_user_role_code_list";
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
        List<String> permissionTexts = dao.queryForOneColumnList(SECURITY_USER_PERMISSION_STRING_LIST, params, String.class);
        List<String> roles = dao.queryForOneColumnList(SECURITY_USER_ROLE_CODE_LIST, params, String.class);
        SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();
        info.addStringPermissions(permissionTexts);
        info.addRoles(roles);
        return info;
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
        return OAuthHelper.getUserInfo(oAuthConfigurationProperties.getUrl(),accessToken);
    }
}
