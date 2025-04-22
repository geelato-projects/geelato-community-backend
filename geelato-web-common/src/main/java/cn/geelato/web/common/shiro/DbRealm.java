/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package cn.geelato.web.common.shiro;

import cn.geelato.core.orm.Dao;
import cn.geelato.security.User;
import cn.geelato.utils.Encodes;
import jakarta.annotation.PostConstruct;
import org.apache.shiro.authc.*;
import org.apache.shiro.authc.credential.HashedCredentialsMatcher;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.util.ByteSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class DbRealm extends AuthorizingRealm {

    @Autowired
    @Qualifier("primaryDao")
    protected Dao dao;
    private final static String SECURITY_USER_PERMISSION_STRING_LIST = "security_user_permission_string_list";
    private final static String SECURITY_USER_ROLE_CODE_LIST = "security_user_role_code_list";

    /**
     * 认证回调函数,登录时调用.
     */
    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken authToken) throws AuthenticationException {
        UsernamePasswordToken token = (UsernamePasswordToken) authToken;
        User user = dao.queryForObject(User.class, "loginName", token.getUsername());
        if (user != null) {
            byte[] salt = Encodes.decodeHex(user.getSalt());
            return new SimpleAuthenticationInfo(
                    new ShiroUser(user.getUserId(), user.getLoginName(), user.getUserName()),
                    user.getPassword(),
                    ByteSource.Util.bytes(salt),
                    getName()
            );
        } else {
            return null;
        }
    }

    /**
     * 授权查询回调函数, 进行鉴权但缓存中无用户的授权信息时调用.
     */
    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        ShiroUser shiroUser = (ShiroUser) principals.getPrimaryPrincipal();
        Map params = new HashMap(1);
        params.put("loginName", shiroUser.loginName);
        List<String> permissionTexts = dao.queryForOneColumnList(SECURITY_USER_PERMISSION_STRING_LIST, params, String.class);
        List<String> roles = dao.queryForOneColumnList(SECURITY_USER_ROLE_CODE_LIST, params, String.class);
        SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();
        info.addStringPermissions(permissionTexts);
        info.addRoles(roles);
        return info;
    }

    /**
     * 设定Password校验的Hash算法与迭代次数.
     */
    @PostConstruct
    public void initCredentialsMatcher() {
        HashedCredentialsMatcher matcher = new HashedCredentialsMatcher("SHA-1");
        matcher.setHashIterations(1024);
        setCredentialsMatcher(matcher);
    }

}
