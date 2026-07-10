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
        LoginResult loginResult = LoginResult.formatLoginResult(user);
        loginResult.setToken(token);
        loginResult.setRoles(null);

        List<UserOrg> userOrgList = userIdentityQueryService.queryOrgListByUserId(user.getId());
        if (!userOrgList.isEmpty()) {
            loginResult.setOrgs(userOrgList);
            String orgId = userIdentityQueryService.containsOrg(userOrgList, securityUser.getOrgId())
                    ? securityUser.getOrgId() : user.getOrgId();
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
            if (StringUtils.isNotBlank(loginResult.getCompanyExtendId())) {
                loginResult.setCompanyExtendId(org == null ? null : org.getExtendId());
            }
            if (StringUtils.isBlank(loginResult.getCompanyName())) {
                loginResult.setCompanyName(org == null ? null : org.getName());
            }
        }

        List<Tenant> tenantList = userIdentityQueryService.queryTenantListByUserId(user.getId());
        loginResult.setTenants(tenantList);
        String tenantCode = userIdentityQueryService.containsTenant(tenantList, securityUser.getTenantCode())
                ? securityUser.getTenantCode() : user.getTenantCode();
        loginResult.setTenantCode(tenantCode);
        userIdentityQueryService.populateRoleInfo(loginResult, user.getId(), null, user.getTenantCode());
        return loginResult;
    }
}
