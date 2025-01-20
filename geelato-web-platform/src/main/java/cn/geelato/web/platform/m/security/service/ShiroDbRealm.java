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
package cn.geelato.web.platform.m.security.service;

import cn.geelato.core.orm.Dao;
import cn.geelato.utils.Encodes;
import cn.geelato.web.platform.m.security.entity.User;
import cn.geelato.web.platform.utils.EncryptUtil;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
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

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ShiroDbRealm extends AuthorizingRealm {

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
                    new ShiroUser(user.getId(), user.getLoginName(), user.getName()),
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
        HashedCredentialsMatcher matcher = new HashedCredentialsMatcher(EncryptUtil.HASH_ALGORITHM);
        matcher.setHashIterations(EncryptUtil.HASH_ITERATIONS);
        setCredentialsMatcher(matcher);
    }

    /**
     * 自定义Authentication对象，使得Subject除了携带用户的登录名外还可以携带更多信息.
     */
    public static class ShiroUser implements Serializable {
        @Serial
        private static final long serialVersionUID = -1373760761780840081L;

        public String id;
        public String loginName;
        @Getter
        public String name;

        public ShiroUser(String id, String loginName, String name) {
            this.id = id;
            this.loginName = loginName;
            this.name = name;
        }

        /**
         * 本函数输出将作为默认的<shiro:principal/>输出.
         */
        @Override
        public String toString() {
            return loginName;
        }

        /**
         * 重载hashCode,只计算loginName;
         */
        @Override
        public int hashCode() {
            return Arrays.hashCode(new String[]{"loginName"});
        }

        /**
         * 重载equals,只计算loginName;
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            ShiroUser other = (ShiroUser) obj;
            if (loginName == null) {
                return other.loginName == null;
            } else return loginName.equals(other.loginName);
        }
    }
}
