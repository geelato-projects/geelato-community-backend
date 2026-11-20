package cn.geelato.web.platform.srv.auth.service;

import cn.geelato.core.orm.Dao;
import cn.geelato.meta.Org;
import cn.geelato.meta.User;
import cn.geelato.security.Tenant;
import cn.geelato.security.UserOrg;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.common.interceptor.UnauthorizedException;
import cn.geelato.web.platform.srv.security.entity.LoginResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CurrentUserProfileService {
    private final Dao dao;
    private final UserIdentityQueryService userIdentityQueryService;

    public CurrentUserProfileService(@Qualifier("primaryDao") Dao dao,
                                     UserIdentityQueryService userIdentityQueryService) {
        this.dao = dao;
        this.userIdentityQueryService = userIdentityQueryService;
    }

    public LoginResult getCurrentUserInfo(cn.geelato.security.User securityUser, String token) {
        if (securityUser == null) {
            throw new UnauthorizedException("获取用户失败");
        }
        User user = dao.queryForObject(User.class, "id", securityUser.getUserId());
        if (user == null) {
            throw new UnauthorizedException("用户不存在或已失效");
        }
        // 委托代办态：securityUser 已由 applyDelegation 注入 delegateUserId（导师），回填供前端展示
        return buildProfile(user, securityUser.getOrgId(), securityUser.getTenantCode(), token,
                securityUser.getDelegateUserId(), securityUser.getDelegateUserName());
    }

    /**
     * 组装与 /api/user/info 同构的完整用户身份（各登录端点复用，字段只增不减）。
     *
     * @param user             落库用户（调用方自查，本方法不回查）
     * @param sessionOrgId     会话主组织（登录/切换身份传所选 org；为空或不在用户组织列表时回落 user.orgId）
     * @param sessionTenantCode 会话租户（同上回落 user.tenantCode）
     */
    public LoginResult buildProfile(User user, String sessionOrgId, String sessionTenantCode, String token,
                                    String delegateUserId, String delegateUserName) {
        LoginResult loginResult = LoginResult.formatLoginResult(user);
        loginResult.setToken(token);
        loginResult.setDelegateUserId(delegateUserId);
        loginResult.setDelegateUserName(delegateUserName);

        List<UserOrg> userOrgList = userIdentityQueryService.queryOrgListByUserId(user.getId());
        if (!userOrgList.isEmpty()) {
            loginResult.setOrgs(userOrgList);
            String orgId = userIdentityQueryService.containsOrg(userOrgList, sessionOrgId)
                    ? sessionOrgId : user.getOrgId();
            UserOrg userOrg = userOrgList.stream()
                    .filter(org -> org.getOrgId().equals(orgId))
                    .findFirst()
                    .orElse(null);
            if (userOrg != null) {
                loginResult.setOrgId(userOrg.getOrgId());
                loginResult.setOrgName(userOrg.getName());
                loginResult.setCompanyId(userOrg.getCompanyId());
                loginResult.setCompanyName(null);
                loginResult.setCompanyExtendId(userOrg.getExtendId());
            }
        }

        if (StringUtils.isNotBlank(loginResult.getCompanyId())) {
            Org org = dao.queryForObject(Org.class, loginResult.getCompanyId());
            if (StringUtils.isBlank(loginResult.getCompanyExtendId())) {
                loginResult.setCompanyExtendId(org == null ? null : org.getExtendId());
            }
            if (StringUtils.isBlank(loginResult.getCompanyName())) {
                loginResult.setCompanyName(org == null ? null : org.getName());
            }
        }

        List<Tenant> tenantList = userIdentityQueryService.queryTenantListByUserId(user.getId());
        loginResult.setTenants(tenantList);
        String tenantCode = userIdentityQueryService.containsTenant(tenantList, sessionTenantCode)
                ? sessionTenantCode : user.getTenantCode();
        loginResult.setTenantCode(tenantCode);
        userIdentityQueryService.populateRoleInfo(loginResult, user.getId(), null, user.getTenantCode());
        return loginResult;
    }
}
