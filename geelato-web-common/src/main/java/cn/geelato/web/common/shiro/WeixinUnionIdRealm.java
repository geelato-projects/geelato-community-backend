package cn.geelato.web.common.shiro;

import cn.geelato.core.orm.Dao;
import cn.geelato.meta.User;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;

public class WeixinUnionIdRealm extends AuthorizingRealm {
    private final Dao dao;

    public WeixinUnionIdRealm(Dao dao) {
        this.dao = dao;
    }

    @Override
    public boolean supports(AuthenticationToken token) {
        return token instanceof WeixinUnionIdToken;
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        return new SimpleAuthorizationInfo();
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken authToken) throws AuthenticationException {
        if (dao == null) {
            return null;
        }
        WeixinUnionIdToken token = (WeixinUnionIdToken) authToken;
        User user = dao.queryForObject(User.class, "weixinUnionId", token.getUnionId());
        if (user == null) {
            return null;
        }
        return new SimpleAuthenticationInfo(
                new ShiroUser(user.getId(), user.getLoginName(), user.getName()),
                token.getUnionId(),
                getName()
        );
    }
}
