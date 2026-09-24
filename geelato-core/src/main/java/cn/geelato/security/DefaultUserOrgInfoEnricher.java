package cn.geelato.security;

public class DefaultUserOrgInfoEnricher implements UserOrgInfoEnricher {
    private final OrgProvider orgProvider;

    public DefaultUserOrgInfoEnricher(OrgProvider orgProvider) {
        this.orgProvider = orgProvider;
    }

    @Override
    public void enrich(User user) {
        if (user == null) {
            return;
        }
        String resolvedOrgId = firstNonBlank(user.getOrgId(), resolveOrgId(user));
        if (resolvedOrgId != null && !resolvedOrgId.isEmpty()) {
            user.setOrgId(resolvedOrgId);
            user.setOrgName(orgProvider.getOrgName(resolvedOrgId));
            String companyId = orgProvider.getCompanyId(resolvedOrgId);
            user.setCompanyId(companyId);
            user.setCompanyName(orgProvider.getCompanyName(resolvedOrgId));
            user.setExtendId(orgProvider.getCompanyExtendId(resolvedOrgId));
            user.setBuId(companyId);
            user.setBuName(user.getCompanyName());
            if (user.getDeptId() == null || user.getDeptId().isEmpty()) {
                user.setDeptId(orgProvider.getDeptId(resolvedOrgId));
            }
        }
        String resolvedDefaultOrgId = firstNonBlank(user.getDefaultOrgId(), resolveDefaultOrgId(user));
        if (resolvedDefaultOrgId != null && !resolvedDefaultOrgId.isEmpty()) {
            user.setDefaultOrgId(resolvedDefaultOrgId);
            user.setDefaultOrgName(orgProvider.getOrgName(resolvedDefaultOrgId));
        }
    }

    @Override
    public void enrich(UserOrg userOrg) {
        if (userOrg == null) {
            return;
        }
        if (userOrg.getOrgId() != null && !userOrg.getOrgId().isEmpty()) {
            if (userOrg.getName() == null || userOrg.getName().isEmpty()) {
                userOrg.setName(orgProvider.getOrgName(userOrg.getOrgId()));
            }
            userOrg.setDeptId(orgProvider.getDeptId(userOrg.getOrgId()));
            userOrg.setCompanyId(orgProvider.getCompanyId(userOrg.getOrgId()));
            userOrg.setExtendId(orgProvider.getCompanyExtendId(userOrg.getOrgId()));
            if (userOrg.getFullName() == null || userOrg.getFullName().isEmpty()) {
                userOrg.setFullName(userOrg.getName());
            }
        }
    }

    private String resolveOrgId(User user) {
        if (user.getUserOrgs() == null || user.getUserOrgs().isEmpty()) {
            return null;
        }
        for (UserOrg userOrg : user.getUserOrgs()) {
            if (Boolean.TRUE.equals(userOrg.getDefaultOrg()) && userOrg.getOrgId() != null && !userOrg.getOrgId().isEmpty()) {
                return userOrg.getOrgId();
            }
        }
        for (UserOrg userOrg : user.getUserOrgs()) {
            if (userOrg.getOrgId() != null && !userOrg.getOrgId().isEmpty()) {
                return userOrg.getOrgId();
            }
        }
        return null;
    }

    private String resolveDefaultOrgId(User user) {
        if (user.getUserOrgs() == null || user.getUserOrgs().isEmpty()) {
            return null;
        }
        for (UserOrg userOrg : user.getUserOrgs()) {
            if (Boolean.TRUE.equals(userOrg.getDefaultOrg()) && userOrg.getOrgId() != null && !userOrg.getOrgId().isEmpty()) {
                return userOrg.getOrgId();
            }
        }
        return null;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isEmpty()) {
            return first;
        }
        return second;
    }
}
