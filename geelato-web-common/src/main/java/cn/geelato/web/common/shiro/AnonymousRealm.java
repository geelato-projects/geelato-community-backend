package cn.geelato.web.common.shiro;

import cn.geelato.core.orm.Dao;
import cn.geelato.meta.User;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;

public class AnonymousRealm extends AuthorizingRealm {

    private static final String ANONYMOUS_FIXED_PASSWORD = "H2k9ZpQ3@geElAto";

    protected Dao dao;

    public AnonymousRealm(Dao dao) {
        this.dao = dao;
    }

    @Override
    public boolean supports(AuthenticationToken token) {
        return token instanceof UsernamePasswordToken;
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        return new SimpleAuthorizationInfo();
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken authToken) throws AuthenticationException {
        UsernamePasswordToken token = (UsernamePasswordToken) authToken;
        String provided = token.getPassword() == null ? null : new String(token.getPassword());
        if (!ANONYMOUS_FIXED_PASSWORD.equals(provided)) {
            return null;
        }
        User user = dao.queryForObject(User.class, "loginName", token.getUsername());
        if (user == null) {
            return null;
        }
        ShiroUser principal = new ShiroUser(user.getId(), user.getLoginName(), user.getName());
        return new org.apache.shiro.authc.SimpleAuthenticationInfo(principal, ANONYMOUS_FIXED_PASSWORD, getName());
    }
}
