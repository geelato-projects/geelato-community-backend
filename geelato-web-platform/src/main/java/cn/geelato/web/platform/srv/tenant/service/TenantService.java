package cn.geelato.web.platform.srv.tenant.service;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.meta.OrgUserMap;
import cn.geelato.meta.Role;
import cn.geelato.meta.RoleUserMap;
import cn.geelato.utils.Digests;
import cn.geelato.utils.Encodes;
import cn.geelato.utils.UUIDUtils;
import cn.geelato.meta.Org;
import cn.geelato.meta.User;
import cn.geelato.web.platform.srv.base.service.BaseService;
import cn.geelato.web.platform.srv.tenant.entity.Tenant;
import cn.geelato.web.platform.srv.tenant.entity.TenantSite;
import cn.geelato.web.platform.srv.security.enums.*;
import cn.geelato.web.platform.srv.security.service.*;
import cn.geelato.web.platform.utils.EncryptUtil;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class TenantService extends BaseService {
    private static final String defaultUserName = "管理员";
    private static final String defaultRoleName = "租户管理员";
    private static final String defaultRoleCode = "admin";

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

    public TenantSite createTenantSite(String name, String domain, String tenantCode) {
        // 检查是否已存在租户站点
        Map<String, Object> params = new HashMap<>();
        params.put("domain", domain);
        params.put("tenantCode", tenantCode);
        List<TenantSite> existingSites = super.queryModel(TenantSite.class, params);
        
        if (!existingSites.isEmpty()) {
            // 已存在租户站点，使用现有的
            return existingSites.get(0);
        }
        
        // 不存在租户站点，创建新的
        TenantSite tenantSite = new TenantSite();
        tenantSite.setName(name);
        tenantSite.setLang("cn");
        tenantSite.setDomain(domain);
        tenantSite.setTenantCode(tenantCode);
        return super.createModel(tenantSite);
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
        // 检查是否已存在以admin开头的登录名
        String loginName = getDefaultLoginName();
        Map<String, Object> params = new HashMap<>();
        params.put("loginName", loginName);
        params.put("tenantCode", tenantCode);
        List<User> existingUsers = userService.queryModel(User.class, params);
        
        // 如果已存在相同登录名，重新生成
        int attempts = 0;
        while (!existingUsers.isEmpty() && attempts < 5) {
            loginName = getDefaultLoginName();
            params.put("loginName", loginName);
            existingUsers = userService.queryModel(User.class, params);
            attempts++;
        }
        
        User user = new User();
        byte[] salts = Digests.generateSalt(EncryptUtil.SALT_SIZE);
        user.setName(defaultUserName);
        user.setLoginName(loginName);
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
        role.setUsedApp(1);
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
        if (plainPassword == null) {
            return Map.of("userName", loginName, "password", "[使用现有密码]");
        }
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
            // 检查是否已存在租户站点
            Map<String, Object> siteParams = new HashMap<>();
            siteParams.put("domain", source.getCompanyDomain());
            siteParams.put("tenantCode", source.getCode());
            List<TenantSite> existingSites = super.queryModel(TenantSite.class, siteParams);
            if (existingSites.isEmpty()) {
                createTenantSite(source.getCompanyName(), source.getCompanyDomain(), source.getCode());
            }
        }
        
        // 检查是否已存在根组织（没有父级的组织）
        Map<String, Object> orgParams = new HashMap<>();
        orgParams.put("tenantCode", source.getCode());
        orgParams.put("pid", null);
        List<Org> rootOrgs = orgService.queryModel(Org.class, orgParams);
        
        Org org;
        if (rootOrgs.isEmpty()) {
            // 不存在根组织，创建新的根组织
            org = createOrg(source.getCompanyName(), source.getCode());
        } else {
            // 已存在根组织，使用现有的
            org = rootOrgs.get(0);
        }
        
        // 检查是否已存在管理员用户
        Map<String, Object> userParams = new HashMap<>();
        userParams.put("type", UserTypeEnum.ADMINISTRATOR.getValue());
        userParams.put("tenantCode", source.getCode());
        // 查找登录名以admin开头的管理员用户
        List<User> adminUsers = userService.queryModel(User.class, userParams);
        
        User user;
        String plainPassword;
        if (!adminUsers.isEmpty()) {
            // 已存在管理员用户，使用现有的
            user = adminUsers.get(0);
            plainPassword = null; // 不重置密码
        } else {
            // 不存在管理员用户，创建新的
            plainPassword = RandomStringUtils.randomAlphanumeric(EncryptUtil.SALT_SIZE);
            user = createUser(org.getId(), org.getName(), source.getMainEmail(), plainPassword, source.getCode());
        }
        
        // 检查是否已存在组织用户映射
        List<OrgUserMap> orgUserMaps = orgUserMapService.queryModelByIds(org.getId(), user.getId());
        if (orgUserMaps.isEmpty()) {
            // 不存在组织用户映射，创建新的
            createOrgUserMap(org.getId(), org.getName(), user.getId(), user.getName(), source.getCode());
        }
        
        // 检查是否已存在角色
        Map<String, Object> roleParams = new HashMap<>();
        roleParams.put("code", defaultRoleCode);
        roleParams.put("tenantCode", source.getCode());
        List<Role> roles = roleService.queryModel(Role.class, roleParams);
        
        Role role;
        if (roles.isEmpty()) {
            // 不存在角色，创建新角色
            role = createRole(source.getCode());
        } else {
            // 已存在角色，使用现有的
            role = roles.get(0);
        }
        
        // 检查是否已存在角色用户映射
        List<RoleUserMap> roleUserMaps = roleUserMapService.queryModelByIds(role.getId(), user.getId());
        if (roleUserMaps.isEmpty()) {
            // 不存在角色用户映射，创建新的映射
            createRoleUserMap(role.getId(), role.getName(), user.getId(), user.getName(), source.getCode());
        }

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
            // 检查是否已存在根组织
            Map<String, Object> orgParams = new HashMap<>();
            orgParams.put("tenantCode", source.getCode());
            orgParams.put("pid", null);
            List<Org> rootOrgs = orgService.queryModel(Org.class, orgParams);
            
            Org org;
            if (rootOrgs.isEmpty()) {
                // 不存在根组织，创建新的
                org = createOrg(target.getCompanyName(), target.getCode());
            } else {
                // 已存在根组织，使用现有的
                org = rootOrgs.get(0);
            }
            
            // 检查是否已存在管理员用户
            Map<String, Object> adminParams = new HashMap<>();
            adminParams.put("type", UserTypeEnum.ADMINISTRATOR.getValue());
            adminParams.put("tenantCode", target.getCode());
            List<User> adminUsers = userService.queryModel(User.class, adminParams);
            
            if (!adminUsers.isEmpty()) {
                // 已存在管理员用户，使用现有的
                user = adminUsers.get(0);
                // 不重置密码
            } else {
                // 不存在管理员用户，创建新的
                plainPassword = RandomStringUtils.randomAlphanumeric(EncryptUtil.SALT_SIZE);
                user = createUser(org.getId(), org.getName(), target.getMainEmail(), plainPassword, target.getCode());
            }
            defaultUserName = user.getLoginName();
            
            // 检查是否已存在组织用户映射
            List<OrgUserMap> orgUserMaps = orgUserMapService.queryModelByIds(org.getId(), user.getId());
            if (orgUserMaps.isEmpty()) {
                // 不存在组织用户映射，创建新的
                createOrgUserMap(org.getId(), org.getName(), user.getId(), user.getName(), target.getCode());
            }
        } else {
            user = userList.get(0);
            if (Strings.isBlank(user.getOrgId())) {
                // 检查是否已存在根组织
                Map<String, Object> orgParams = new HashMap<>();
                orgParams.put("tenantCode", source.getCode());
                orgParams.put("pid", null);
                List<Org> rootOrgs = orgService.queryModel(Org.class, orgParams);
                
                Org org;
                if (rootOrgs.isEmpty()) {
                    // 不存在根组织，创建新的
                    org = createOrg(target.getCompanyName(), target.getCode());
                } else {
                    // 已存在根组织，使用现有的
                    org = rootOrgs.get(0);
                }
                
                user.setOrgId(org.getId());
                user.setOrgName(org.getName());
                userService.updateModel(user);
                
                // 检查是否已存在组织用户映射
                List<OrgUserMap> orgUserMaps = orgUserMapService.queryModelByIds(org.getId(), user.getId());
                if (orgUserMaps.isEmpty()) {
                    // 不存在组织用户映射，创建新的
                    createOrgUserMap(org.getId(), org.getName(), user.getId(), user.getName(), target.getCode());
                }
            }
            user.setEmail(target.getMainEmail());
            userService.updateModel(user);
            defaultUserName = user.getLoginName();
        }
        // 检查是否已存在角色
        Map<String, Object> roleParams = new HashMap<>();
        roleParams.put("code", defaultRoleCode);
        roleParams.put("tenantCode", target.getCode());
        List<Role> roleList = roleService.queryModel(Role.class, roleParams);
        
        if (roleList.isEmpty()) {
            // 不存在角色，创建新角色
            Role role = createRole(target.getCode());
            // 检查是否已存在角色用户映射
            List<RoleUserMap> roleUserMaps = roleUserMapService.queryModelByIds(role.getId(), user.getId());
            if (roleUserMaps.isEmpty()) {
                // 不存在角色用户映射，创建新的映射
                createRoleUserMap(role.getId(), role.getName(), user.getId(), user.getName(), target.getCode());
            }
        } else {
            // 已存在角色，检查是否已存在角色用户映射
            Role role = roleList.get(0);
            List<RoleUserMap> roleUserMaps = roleUserMapService.queryModelByIds(role.getId(), user.getId());
            if (roleUserMaps.isEmpty()) {
                // 不存在角色用户映射，创建新的映射
                createRoleUserMap(role.getId(), role.getName(), user.getId(), user.getName(), target.getCode());
            }
        }

        return buildResult(defaultUserName, plainPassword);
    }

    public Map<String, String> resetPassword(Tenant source) {
        String plainPassword = RandomStringUtils.randomAlphanumeric(EncryptUtil.SALT_SIZE);
        String defaultUserName;
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
            // 检查是否已存在根组织
            Map<String, Object> orgParams = new HashMap<>();
            orgParams.put("tenantCode", source.getCode());
            orgParams.put("pid", null);
            List<Org> rootOrgs = orgService.queryModel(Org.class, orgParams);
            
            Org org;
            if (rootOrgs.isEmpty()) {
                // 不存在根组织，创建新的
                org = createOrg(source.getCompanyName(), source.getCode());
            } else {
                // 已存在根组织，使用现有的
                org = rootOrgs.get(0);
            }
            
            // 检查是否已存在管理员用户
            Map<String, Object> adminParams = new HashMap<>();
            adminParams.put("type", UserTypeEnum.ADMINISTRATOR.getValue());
            adminParams.put("tenantCode", source.getCode());
            List<User> adminUsers = userService.queryModel(User.class, adminParams);
            
            User user;
            if (!adminUsers.isEmpty()) {
                // 已存在管理员用户，使用现有的
                user = adminUsers.get(0);
                user.setPassword(EncryptUtil.encryptPassword(plainPassword, user.getSalt()));
                userService.updateModel(user);
            } else {
                // 不存在管理员用户，创建新的
                user = createUser(org.getId(), org.getName(), source.getMainEmail(), plainPassword, source.getCode());
            }
            defaultUserName = user.getLoginName();
            
            // 检查是否已存在组织用户映射
            List<OrgUserMap> orgUserMaps = orgUserMapService.queryModelByIds(org.getId(), user.getId());
            if (orgUserMaps.isEmpty()) {
                // 不存在组织用户映射，创建新的
                createOrgUserMap(org.getId(), org.getName(), user.getId(), user.getName(), source.getCode());
            }
            
            // 检查是否已存在角色
            Map<String, Object> roleParams = new HashMap<>();
            roleParams.put("code", defaultRoleCode);
            roleParams.put("tenantCode", source.getCode());
            List<Role> roleList = roleService.queryModel(Role.class, roleParams);
            
            Role role;
            if (roleList.isEmpty()) {
                // 不存在角色，创建新角色
                role = createRole(source.getCode());
            } else {
                // 已存在角色，使用现有的
                role = roleList.get(0);
            }
            
            // 检查是否已存在角色用户映射
            List<RoleUserMap> roleUserMaps = roleUserMapService.queryModelByIds(role.getId(), user.getId());
            if (roleUserMaps.isEmpty()) {
                // 不存在角色用户映射，创建新的映射
                createRoleUserMap(role.getId(), role.getName(), user.getId(), user.getName(), source.getCode());
            }
        }
        return buildResult(defaultUserName, plainPassword);
    }
}