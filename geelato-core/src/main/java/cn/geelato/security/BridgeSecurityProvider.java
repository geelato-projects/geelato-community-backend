package cn.geelato.security;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link SecurityProvider} 的本地库桥接实现：组合 {@link OrgProvider}/{@link UserProvider}/
 * {@link RoleProvider} 三个既有 Provider 的数据面，就地推导契约查询（含人员求解与组织树），
 * 不发起任何远程调用——与认证中心开放 API 实现（AuthCenterSecurityProvider）相对，
 * 适用于子系统与平台库同库/同网、无需经认证中心的场景。
 * <p>
 * 数据面要求：
 * <ul>
 *   <li>正向单查/批查：三个 Provider 的单查能力即可；</li>
 *   <li>人员求解（部门/角色/部门角色下的人）：依赖 {@link UserProvider#getAllUsers()}
 *       （快照式实现天然支持；直连式实现返回空时，人员求解随之返回空）；</li>
 *   <li>组织树（子部门/祖先链）：依赖 {@link OrgProvider#getAllOrgs()}；祖先链仅需单查
 *       （沿 pid 上溯），无全量要求；</li>
 *   <li>角色相关（角色单查/部门下的角色）：依赖 {@link RoleProvider}，缺省（null）时降级返回空。</li>
 * </ul>
 * 边界：权限查询（queryPermissionsOfRole/queryPermissionsOfUser）不实现，保持 default 空返回——
 * 权限域数据在认证中心侧，请使用 AuthCenterSecurityProvider。
 * <p>
 * 人员求解为内存过滤（与快照式全量装载架构一致，参与者解析非超热路径）；
 * 结果按 userId 排序后切片分页，保证同一数据快照下分页稳定。
 * 每次调用即时取 Provider 当前快照推导，不自行缓存，天然与 Provider 的 refresh 语义一致。
 */
public class BridgeSecurityProvider implements SecurityProvider {

    private final OrgProvider orgProvider;
    private final UserProvider userProvider;
    private final RoleProvider roleProvider;

    public BridgeSecurityProvider(OrgProvider orgProvider, UserProvider userProvider) {
        this(orgProvider, userProvider, null);
    }

    public BridgeSecurityProvider(OrgProvider orgProvider, UserProvider userProvider, RoleProvider roleProvider) {
        this.orgProvider = orgProvider;
        this.userProvider = userProvider;
        this.roleProvider = roleProvider;
    }

    // ==================== 一、实体单查 ====================

    @Override
    public User getUserById(String userId) {
        return userProvider.getUser(userId);
    }

    @Override
    public User getUserByLoginName(String loginName) {
        return userProvider.getUserByExtendKey(loginName, "loginName");
    }

    @Override
    public Org getOrgById(String orgId) {
        return orgProvider.getOrg(orgId);
    }

    @Override
    public Org getOrgByCode(String orgCode) {
        if (orgCode == null || orgCode.isEmpty()) {
            return null;
        }
        for (Org org : orgProvider.getAllOrgs()) {
            if (org != null && orgCode.equals(org.getCode())) {
                return org;
            }
        }
        return null;
    }

    @Override
    public Role getRoleById(String roleId) {
        return roleProvider == null ? null : roleProvider.getRole(roleId);
    }

    @Override
    public Role getRoleByCode(String roleCode) {
        return roleProvider == null ? null : roleProvider.getRoleByCode(roleCode);
    }

    @Override
    public UserRoles getUserRoles(String userId) {
        UserRoles userRoles = new UserRoles();
        userRoles.setUserRoles(userProvider.getUserRoles(userId));
        userRoles.setUserOrgRoles(userProvider.getUserOrgRoles(userId));
        return userRoles;
    }

    @Override
    public List<UserOrg> getUserOrgs(String userId) {
        return userProvider.getUserOrgs(userId);
    }

    // ==================== 二、实体批查 ====================

    @Override
    public List<User> queryUsersByIds(List<String> userIds) {
        List<User> users = new ArrayList<>();
        if (userIds == null) {
            return users;
        }
        for (String userId : userIds) {
            User user = userProvider.getUser(userId);
            if (user != null) {
                users.add(user);
            }
        }
        return users;
    }

    @Override
    public List<Org> queryOrgsByIds(List<String> orgIds) {
        List<Org> orgs = new ArrayList<>();
        if (orgIds == null) {
            return orgs;
        }
        for (String orgId : orgIds) {
            Org org = orgProvider.getOrg(orgId);
            if (org != null) {
                orgs.add(org);
            }
        }
        return orgs;
    }

    @Override
    public List<Role> queryRolesByIds(List<String> roleIds) {
        List<Role> roles = new ArrayList<>();
        if (roleIds == null || roleProvider == null) {
            return roles;
        }
        for (String roleId : roleIds) {
            Role role = roleProvider.getRole(roleId);
            if (role != null) {
                roles.add(role);
            }
        }
        return roles;
    }

    // ==================== 三、人员求解 ====================

    @Override
    public Page<User> pageQueryUsersOfOrg(String orgId, boolean includeSubOrgs, PageParam pageParam) {
        Set<String> targetOrgIds = targetOrgIds(orgId, includeSubOrgs);
        List<User> matched = new ArrayList<>();
        for (User user : userProvider.getAllUsers()) {
            if (user != null && belongsToAnyOrg(user, targetOrgIds)) {
                matched.add(user);
            }
        }
        return pageOf(matched, pageParam);
    }

    @Override
    public List<User> queryUsersOfOrg(String orgId, boolean includeSubOrgs) {
        Set<String> targetOrgIds = targetOrgIds(orgId, includeSubOrgs);
        List<User> matched = new ArrayList<>();
        for (User user : userProvider.getAllUsers()) {
            if (user != null && belongsToAnyOrg(user, targetOrgIds)) {
                matched.add(user);
            }
        }
        return matched;
    }

    @Override
    public Page<User> pageQueryUsersOfRole(String roleCode, PageParam pageParam) {
        return pageOf(filterUsersOfRole(roleCode), pageParam);
    }

    @Override
    public List<User> queryUsersOfRole(String roleCode) {
        return filterUsersOfRole(roleCode);
    }

    @Override
    public Page<User> pageQueryUsersOfOrgRole(String orgId, String roleCode, boolean includeSubOrgs, PageParam pageParam) {
        return pageOf(filterUsersOfOrgRole(orgId, roleCode, includeSubOrgs), pageParam);
    }

    @Override
    public List<User> queryUsersOfOrgRole(String orgId, String roleCode, boolean includeSubOrgs) {
        return filterUsersOfOrgRole(orgId, roleCode, includeSubOrgs);
    }

    // ==================== 四、部门角色与组织树 ====================

    @Override
    public List<OrgRole> queryOrgRoles(String orgId, boolean includeSubOrgs) {
        if (roleProvider == null || orgId == null || orgId.isEmpty()) {
            return Collections.emptyList();
        }
        List<OrgRole> orgRoles = new ArrayList<>();
        for (String targetOrgId : targetOrgIds(orgId, includeSubOrgs)) {
            orgRoles.addAll(roleProvider.getOrgRoles(targetOrgId));
        }
        return orgRoles;
    }

    @Override
    public List<Org> querySubOrgs(String orgId, boolean recursive) {
        if (orgId == null || orgId.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, List<Org>> childrenIndex = childrenIndex();
        List<Org> result = new ArrayList<>();
        Deque<String> pending = new ArrayDeque<>();
        List<Org> directChildren = childrenIndex.get(orgId);
        if (directChildren == null) {
            return result;
        }
        result.addAll(directChildren);
        if (recursive) {
            pending.addAll(directChildren.stream().map(Org::getOrgId).toList());
            Set<String> visited = new HashSet<>(pending);
            while (!pending.isEmpty()) {
                String current = pending.poll();
                List<Org> children = childrenIndex.get(current);
                if (children == null) {
                    continue;
                }
                for (Org child : children) {
                    if (child != null && child.getOrgId() != null && visited.add(child.getOrgId())) {
                        result.add(child);
                        pending.add(child.getOrgId());
                    }
                }
            }
        }
        return result;
    }

    @Override
    public List<Org> queryAncestorOrgs(String orgId) {
        List<Org> ancestors = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        String currentId = orgId;
        while (currentId != null && !currentId.isEmpty() && visited.add(currentId)) {
            Org current = orgProvider.getOrg(currentId);
            if (current == null || current.getPid() == null || current.getPid().isEmpty()) {
                break;
            }
            Org parent = orgProvider.getOrg(current.getPid());
            if (parent == null) {
                break;
            }
            ancestors.add(parent);
            currentId = parent.getOrgId();
        }
        return ancestors;
    }

    // ==================== 私有：过滤与索引 ====================

    /** 直挂角色包含该 code 的用户（按 code 匹配，无需解析角色 ID）。 */
    private List<User> filterUsersOfRole(String roleCode) {
        List<User> matched = new ArrayList<>();
        if (roleCode == null || roleCode.isEmpty()) {
            return matched;
        }
        for (User user : userProvider.getAllUsers()) {
            if (user == null || user.getUserRoles() == null) {
                continue;
            }
            for (UserRole userRole : user.getUserRoles()) {
                if (userRole != null && roleCode.equals(userRole.getCode())) {
                    matched.add(user);
                    break;
                }
            }
        }
        return matched;
    }

    /** 经"用户挂组织角色"直挂、来源部门命中目标部门（可含下级）的用户，按用户去重。 */
    private List<User> filterUsersOfOrgRole(String orgId, String roleCode, boolean includeSubOrgs) {
        List<User> matched = new ArrayList<>();
        if (orgId == null || orgId.isEmpty() || roleCode == null || roleCode.isEmpty()) {
            return matched;
        }
        Set<String> targetOrgIds = targetOrgIds(orgId, includeSubOrgs);
        for (User user : userProvider.getAllUsers()) {
            if (user == null || user.getUserOrgRoles() == null) {
                continue;
            }
            boolean hit = false;
            for (UserOrgRole userOrgRole : user.getUserOrgRoles()) {
                if (userOrgRole == null) {
                    continue;
                }
                if (roleCode.equals(userOrgRole.getCode())
                        && userOrgRole.getOrgId() != null
                        && targetOrgIds.contains(userOrgRole.getOrgId())) {
                    hit = true;
                    break;
                }
            }
            if (hit) {
                matched.add(user);
            }
        }
        return matched;
    }

    /** 用户的挂靠组织（成员身份）是否命中目标部门集合。 */
    private boolean belongsToAnyOrg(User user, Set<String> targetOrgIds) {
        if (user.getUserOrgs() == null) {
            return false;
        }
        for (UserOrg userOrg : user.getUserOrgs()) {
            if (userOrg != null && userOrg.getOrgId() != null && targetOrgIds.contains(userOrg.getOrgId())) {
                return true;
            }
        }
        return false;
    }

    /** 目标部门集合：仅本部门，或本部门及全部下级。 */
    private Set<String> targetOrgIds(String orgId, boolean includeSubOrgs) {
        if (orgId == null || orgId.isEmpty()) {
            return Collections.emptySet();
        }
        if (!includeSubOrgs) {
            return Collections.singleton(orgId);
        }
        Set<String> ids = new LinkedHashSet<>();
        ids.add(orgId);
        for (Org subOrg : querySubOrgs(orgId, true)) {
            if (subOrg != null && subOrg.getOrgId() != null) {
                ids.add(subOrg.getOrgId());
            }
        }
        return ids;
    }

    /** 由全量组织构建 pid → 直接下级 索引。 */
    private Map<String, List<Org>> childrenIndex() {
        Map<String, List<Org>> index = new LinkedHashMap<>();
        for (Org org : orgProvider.getAllOrgs()) {
            if (org == null || org.getPid() == null || org.getPid().isEmpty() || org.getOrgId() == null) {
                continue;
            }
            index.computeIfAbsent(org.getPid(), k -> new ArrayList<>()).add(org);
        }
        return index;
    }

    /** 内存分页：按 userId 排序保证稳定，再按分页参数切片。 */
    private static Page<User> pageOf(List<User> users, PageParam pageParam) {
        PageParam param = pageParam == null ? PageParam.of(PageParam.DEFAULT_PAGE_NUM, PageParam.DEFAULT_PAGE_SIZE) : pageParam;
        users.sort(Comparator.comparing(u -> u.getUserId() == null ? "" : u.getUserId()));
        int from = Math.min(param.getOffset(), users.size());
        int to = Math.min(from + param.getPageSize(), users.size());
        boolean hasMore = to < users.size();
        return Page.of(new ArrayList<>(users.subList(from, to)), users.size(), param.getPageNum(), param.getPageSize(), hasMore);
    }
}
