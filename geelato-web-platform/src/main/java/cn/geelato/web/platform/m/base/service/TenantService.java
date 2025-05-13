package cn.geelato.web.platform.m.base.service;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.utils.Digests;
import cn.geelato.utils.Encodes;
import cn.geelato.utils.UUIDUtils;
import cn.geelato.web.common.security.Org;
import cn.geelato.web.common.security.User;
import cn.geelato.web.platform.m.base.entity.Tenant;
import cn.geelato.web.platform.m.base.entity.TenantSite;
import cn.geelato.web.platform.m.security.entity.*;
import cn.geelato.web.platform.m.security.enums.*;
import cn.geelato.web.platform.m.security.service.*;
import cn.geelato.web.platform.utils.EncryptUtil;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class TenantService extends BaseService {
    private static final String defaultUserName = "管理员";
    private static final String defaultRoleName = "平台维护员";
    private static final String defaultRoleCode = "super_admin";

    @Lazy
    @Autowired
    private OrgService orgService;
    @Lazy
    @Autowired
    private UserService userService;
    @Lazy
    @Autowired
    private OrgUserMapService orgUserMapService;
    @Lazy
    @Autowired
    private RoleService roleService;
    @Lazy
    @Autowired
    private RoleUserMapService roleUserMapService;

    public String getDefaultLoginName() {
        return "admin" + UUIDUtils.generateNumberAndLowerChars(5).toLowerCase(Locale.ENGLISH);
    }

    public void createTenantSite(String name, String domain, String tenantCode) {
        TenantSite tenantSite = new TenantSite();
        tenantSite.setName(name);
        tenantSite.setLang("cn");
        tenantSite.setDomain(domain);
        tenantSite.setTenantCode(tenantCode);
        super.createModel(tenantSite);
    }

    public Org createOrg(String name, String tenantCode) {
        Org org = new Org();
        org.setName(name);
        org.setCode(UUIDUtils.generateRandom(13));
        org.setStatus(ColumnDefault.ENABLE_STATUS_VALUE);
        org.setType(OrgTypeEnum.COMPANY.getValue());
        org.setCategory(OrgCategoryEnum.INSIDE.getValue());
        org.setTenantCode(tenantCode);
        return orgService.createModel(org);
    }

    public User createUser(String orgId, String orgName, String email, String plainPassword, String tenantCode) {
        User user = new User();
        byte[] salts = Digests.generateSalt(EncryptUtil.SALT_SIZE);
        user.setName(defaultUserName);
        user.setLoginName(getDefaultLoginName());
        user.setOrgId(orgId);
        user.setOrgName(orgName);
        user.setEmail(email);
        user.setSex(UserSexEnum.FEMALE.getValue());
        user.setType(UserTypeEnum.ADMINISTRATOR.getValue());
        user.setSource(UserSourceEnum.LOCAL_USER.getValue());
        user.setPassword(Encodes.encodeHex(Digests.sha1(plainPassword.getBytes(), salts, EncryptUtil.HASH_ITERATIONS)));
        user.setSalt(Encodes.encodeHex(salts));
        user.setEnableStatus(ColumnDefault.ENABLE_STATUS_VALUE);
        user.setTenantCode(tenantCode);
        return userService.createModel(user);
    }

    public void createOrgUserMap(String orgId, String orgName, String userId, String userName, String tenantCode) {
        OrgUserMap orgUserMap = new OrgUserMap();
        orgUserMap.setOrgId(orgId);
        orgUserMap.setOrgName(orgName);
        orgUserMap.setUserId(userId);
        orgUserMap.setUserName(userName);
        orgUserMap.setDefaultOrg(IsDefaultOrgEnum.IS.getValue());
        orgUserMap.setTenantCode(tenantCode);
        orgUserMapService.createModel(orgUserMap);
    }

    public Role createRole(String tenantCode) {
        Role role = new Role();
        role.setName(defaultRoleName);
        role.setCode(defaultRoleCode);
        role.setType(RoleTypeEnum.PLATFORM.getValue());
        role.setWeight(1);
        role.setUsedApp(true);
        role.setEnableStatus(ColumnDefault.ENABLE_STATUS_VALUE);
        role.setTenantCode(tenantCode);
        return roleService.createModel(role);
    }

    public void createRoleUserMap(String roleId, String roleName, String userId, String userName, String tenantCode) {
        RoleUserMap roleUserMap = new RoleUserMap();
        roleUserMap.setRoleId(roleId);
        roleUserMap.setRoleName(roleName);
        roleUserMap.setUserId(userId);
        roleUserMap.setUserName(userName);
        roleUserMap.setTenantCode(tenantCode);
        roleUserMapService.createModel(roleUserMap);
    }

    public Map<String, String> buildResult(String loginName, String plainPassword) {
        return Map.of("userName", loginName, "password", plainPassword);
    }

    /**
     * 在创建租户后，需要创建以下数据：
     * 1. platform_tenant_site
     * 2. platform_org
     * 3. platform_user
     * 4. platform_org_r_user
     * 5. platform_role
     * 6. platform_role_r_user
     */
    public Map<String, String> afterCreate(Tenant source) {
        if (Strings.isBlank(source.getCompanyName())) {
            throw new RuntimeException("租户名称不能为空");
        }
        // platform_tenant_site
        if (Strings.isNotBlank(source.getCompanyDomain())) {
            createTenantSite(source.getCompanyName(), source.getCompanyDomain(), source.getCode());
        }
        // platform_org
        Org org = createOrg(source.getCompanyName(), source.getCode());
        // platform_user
        String plainPassword = RandomStringUtils.randomAlphanumeric(EncryptUtil.SALT_SIZE);
        User user = createUser(org.getId(), org.getName(), source.getMainEmail(), plainPassword, source.getCode());
        // platform_org_r_user
        createOrgUserMap(org.getId(), org.getName(), user.getId(), user.getName(), source.getCode());
        // platform_role
        Role role = createRole(source.getCode());
        // platform_role_r_user
        createRoleUserMap(role.getId(), role.getName(), user.getId(), user.getName(), source.getCode());

        return buildResult(user.getLoginName(), plainPassword);
    }

    /**
     * 在更新租户时，需要更新以下数据：
     * 1. platform_tenant_site，
     * 2. platform_org
     * 3. platform_user
     * 4. platform_org_r_user
     * 5. platform_role
     * 6. platform_role_r_user
     */
    public Map<String, String> beforeUpdate(Tenant source, Tenant target) {
        if (Strings.isBlank(source.getCompanyName()) || Strings.isBlank(source.getCode())) {
            throw new RuntimeException("租户名称，编号不能为空");
        }
        if (!source.getCode().equals(target.getCode())) {
            throw new RuntimeException("租户编号不能修改");
        }
        // platform_tenant_site
        if (Strings.isNotBlank(source.getCompanyDomain())) {
            Map<String, Object> params = Map.of("domain", source.getCompanyDomain(), "tenantCode", source.getCode());
            List<TenantSite> tenantSiteList = super.queryModel(TenantSite.class, params);
            if (tenantSiteList.isEmpty()) {
                createTenantSite(target.getCompanyName(), target.getCompanyDomain(), target.getCode());
            }
        }
        // platform_user
        Map<String, Object> userParams = Map.of("type", UserTypeEnum.ADMINISTRATOR.getValue(), "tenantCode", source.getCode());
        List<User> userList = userService.queryModel(User.class, userParams);
        User user;
        String plainPassword = null;
        String defaultUserName = null;
        if (userList.isEmpty()) {
            // platform_org
            Org org = createOrg(target.getCompanyName(), target.getCode());
            // platform_user
            plainPassword = RandomStringUtils.randomAlphanumeric(EncryptUtil.SALT_SIZE);
            user = createUser(org.getId(), org.getName(), target.getMainEmail(), plainPassword, target.getCode());
            defaultUserName = user.getLoginName();
            // platform_org_r_user
            createOrgUserMap(org.getId(), org.getName(), user.getId(), user.getName(), target.getCode());
        } else {
            user = userList.get(0);
            if (Strings.isBlank(user.getOrgId())) {
                // platform_org
                Org org = createOrg(target.getCompanyName(), target.getCode());
                user.setOrgId(org.getId());
                user.setOrgName(org.getName());
                // platform_org_r_user
                createOrgUserMap(org.getId(), org.getName(), user.getId(), user.getName(), target.getCode());
            }
            user.setEmail(target.getMainEmail());
            userService.updateModel(user);
            defaultUserName = user.getLoginName();
        }
        // platform_role
        Map<String, Object> roleParams = Map.of("code", defaultRoleCode, "type", RoleTypeEnum.PLATFORM.getValue(), "tenantCode", target.getCode());
        List<Role> roleList = roleService.queryModel(Role.class, roleParams);
        if (roleList.isEmpty()) {
            // platform_role
            Role role = createRole(target.getCode());
            // platform_role_r_user
            createRoleUserMap(role.getId(), role.getName(), user.getId(), user.getName(), target.getCode());
        }

        return buildResult(defaultUserName, plainPassword);
    }

    public Map<String, String> resetPassword(Tenant source) {
        String plainPassword = RandomStringUtils.randomAlphanumeric(EncryptUtil.SALT_SIZE);
        String defaultUserName = null;
        // platform_user
        Map<String, Object> userParams = Map.of("type", UserTypeEnum.ADMINISTRATOR.getValue(), "tenantCode", source.getCode());
        List<User> userList = super.queryModel(User.class, userParams, BaseService.DEFAULT_ORDER_BY);
        if (!userList.isEmpty()) {
            User user = userList.get(0);
            user.setPassword(EncryptUtil.encryptPassword(plainPassword, user.getSalt()));
            userService.updateModel(user);
            for (int i = 1; i < userList.size(); i++) {
                userService.isDeleteModel(userList.get(i));
            }
            defaultUserName = user.getLoginName();
        } else {
            // platform_org
            Org org = createOrg(source.getCompanyName(), source.getCode());
            // platform_user
            User user = createUser(org.getId(), org.getName(), source.getMainEmail(), plainPassword, source.getCode());
            defaultUserName = user.getLoginName();
            // platform_org_r_user
            createOrgUserMap(org.getId(), org.getName(), user.getId(), user.getName(), source.getCode());
            // platform_role
            Role role = createRole(source.getCode());
            // platform_role_r_user
            createRoleUserMap(role.getId(), role.getName(), user.getId(), user.getName(), source.getCode());
        }
        return buildResult(defaultUserName, plainPassword);
    }
}
