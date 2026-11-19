package cn.geelato.web.common.security.auth;

import cn.geelato.core.env.EnvManager;
import cn.geelato.security.OrgProvider;
import cn.geelato.security.User;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.common.interceptor.UnauthorizedException;
import cn.geelato.web.common.security.spi.TokenAuthenticator;
import org.springframework.beans.BeanUtils;

/**
 * 认证器公共支撑：用户装载 + 组织信息补全。均迁移自原 DefaultSecurityInterceptor。
 */
public abstract class AbstractTokenAuthenticator implements TokenAuthenticator {

    protected final OrgProvider orgProvider;

    protected AbstractTokenAuthenticator(OrgProvider orgProvider) {
        this.orgProvider = orgProvider;
    }

    /**
     * 按登录名+租户装载会话用户并补全组织信息。找不到用户直接 401（含根因），不做静默兜底。
     */
    protected User loadUser(String loginName, String tenantCode) {
        User currentUser = copyOf(EnvManager.singleInstance().InitCurrentUser(loginName, tenantCode));
        if (currentUser == null || StringUtils.isEmpty(currentUser.getLoginName())) {
            throw new UnauthorizedException("用户不存在: " + loginName);
        }
        currentUser.setupOrgInfo(orgProvider);
        return currentUser;
    }

    /** EnvManager 缓存的是共享实例，切换身份等修改前必须先克隆，防止多 token 互相覆盖。 */
    protected User copyOf(User source) {
        if (source == null) {
            return null;
        }
        User copy = new User();
        BeanUtils.copyProperties(source, copy);
        // BeanUtils 对 Map 只拷引用：隔离 extendInfo，防止会话副本变更污染共享快照
        if (source.getExtendInfo() != null) {
            copy.setExtendInfo(new java.util.LinkedHashMap<>(source.getExtendInfo()));
        }
        return copy;
    }

    /** 以 token 指定的 orgId 覆盖默认组织信息（orgId 为空时跳过）。 */
    protected void setupOrgInfo(User currentUser, String orgId) {
        if (currentUser == null || StringUtils.isEmpty(orgId)) {
            return;
        }
        currentUser.setOrgId(orgId);
        currentUser.setOrgName(orgProvider.getOrgName(orgId));
        currentUser.setDeptId(orgProvider.getDeptId(orgId));
        currentUser.setCompanyId(orgProvider.getCompanyId(orgId));
        String companyName = orgProvider.getCompanyName(orgId);
        currentUser.setCompanyName(companyName);
        currentUser.setBuId(orgProvider.getBuId(orgId));
        currentUser.setBuName(companyName);
        currentUser.setExtendId(orgProvider.getCompanyExtendId(orgId));
    }
}
