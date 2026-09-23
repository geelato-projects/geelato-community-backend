package cn.geelato.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * 安全数据提供者：四实体（组织/用户/角色/权限）与四类挂靠关系的按需单点查询，
 * 供工作流办理人求解、规则/异常告警的参与者解析、数据权限判定等场景消费。
 * <p>
 * 方法分六组（对齐业界标杆：Zanzibar/K8s SubjectAccessReview 的决策类、Casbin/Graph 的
 * 层级传递、Graph $batch 的批查、Keycloak/Okta 的分页）：
 * <ol>
 *   <li>实体单查（get*，返回单对象）；</li>
 *   <li>实体批查（query*ByIds，批量按 ID，减少网络 N+1）；</li>
 *   <li>人员求解（query*，部门/角色/部门角色维度求参与者——工作流与告警的高频入口）；</li>
 *   <li>部门角色与组织树（query*，层级传递，支撑"部门及下级"数据权限场景）；</li>
 *   <li>权限查询；</li>
 *   <li>决策判断（hasRole/hasPermission，业务代码最高频入口）。</li>
 * </ol>
 * 命名约定：
 * <ul>
 *   <li>get* —— 单实体查询（返回单对象或单用户的聚合视图）；</li>
 *   <li>query* —— 列表全量查询；</li>
 *   <li>pageQuery* —— 分页查询。</li>
 * </ul>
 * 参数约定：
 * <ul>
 *   <li>appCode/tenantCode 由实现方绑定（配置或客户端身份携带），契约方法不携带租户/子系统参数；</li>
 *   <li>角色引用一律用 code（跨环境稳定），人员求解方法入参为 roleCode；
 *       仅实体批查（queryRolesByIds）与角色权限查询（queryPermissionsOfRole）按 ID——
 *       它们服务于"手上已有 ID 列表/角色对象"的回填场景，不作业务引用入口；</li>
 *   <li>组织引用一律用 orgId；部门相关的人员/角色求解均带 includeSubOrgs 参数
 *       （是否含子部门，true = 本部门及全部下级，逐层递归）；
 *       <b>不带该参数的重载（如 queryUsersOfOrg(orgId)）默认递归含全部子部门</b>；
 *       子部门查询 querySubOrgs(orgId, recursive) 中 recursive 控制直接下级/全部下级，
 *       querySubOrgs(orgId) 同样默认全部下级。</li>
 * </ul>
 * 实现约定：
 * <ul>
 *   <li>所有方法均为 default 空实现，接入系统按需覆写部分方法即可接入契约；
 *       认证中心的 geelato-auth-client 提供走开放 API 的默认实现（AuthCenterSecurityProvider）；
 *       本地库场景提供桥接 Org/User/Role 三 Provider 的实现（BridgeSecurityProvider，geelato-core）；</li>
 *   <li>pageQuery*（带全部参数的完整体）为核心抽象，实现方覆写；无参标志的
 *       query* 与 pageQuery* 重载为 default 推导（默认递归）；query* 全量为 default
 *       自动翻页聚合（固定每次 200 条），聚合总量超 {@link #MAX_AGGREGATE_SIZE} 抛异常——
 *       宁可失败也不静默截断，大基数场景请改用分页重载；</li>
 *   <li>与 {@link UserProvider}/{@link OrgProvider}/{@link RoleProvider}（快照式全量装载）并存：
 *       本接口面向按需单点查询，各司其职。</li>
 * </ul>
 */
public interface SecurityProvider {

    /**
     * List 全量聚合的安全上限：超过即抛 IllegalStateException，
     * 防止大基数场景（如全公司用户）打爆内存与网络。
     */
    int MAX_AGGREGATE_SIZE = 10_000;

    // ==================== 一、实体单查（get） ====================

    /**
     * 根据用户ID获取用户信息。
     *
     * @return 用户；不存在或无权限可见时返回 null
     */
    default User getUserById(String userId) {
        return null;
    }

    /**
     * 根据登录名获取用户（通知/告警场景按登录名找人的入口）。
     *
     * @return 用户；不存在时返回 null
     */
    default User getUserByLoginName(String loginName) {
        return null;
    }

    /**
     * 根据组织ID获取组织信息。
     *
     * @return 组织；不存在或无权限可见时返回 null
     */
    default Org getOrgById(String orgId) {
        return null;
    }

    /**
     * 根据组织编码获取组织。
     *
     * @return 组织；不存在或无权限可见时返回 null
     */
    default Org getOrgByCode(String orgCode) {
        return null;
    }

    /**
     * 根据角色ID获取角色（回填场景：手上已有角色 ID，如 getUserRoles/queryOrgRoles 的结果）。
     *
     * @return 角色；不存在或无权限可见时返回 null
     */
    default Role getRoleById(String roleId) {
        return null;
    }

    /**
     * 根据角色编码获取角色（业务代码与数据权限规则引用角色一律用 code，不用 ID）。
     *
     * @return 角色；不存在或无权限可见时返回 null
     */
    default Role getRoleByCode(String roleCode) {
        return null;
    }

    /**
     * 用户的角色，区分直挂角色（userRoles）与组织挂靠角色（userOrgRoles，含来源部门）。
     * 生效角色（并集去重）经 {@link UserRoles#getEffectiveRoles()} 获取。
     */
    default UserRoles getUserRoles(String userId) {
        return new UserRoles();
    }

    /**
     * 用户挂靠的组织（成员身份，多部门挂靠一并返回；与角色挂靠无关）。
     */
    default List<UserOrg> getUserOrgs(String userId) {
        return Collections.emptyList();
    }

    // ==================== 二、实体批查（query，减少网络 N+1） ====================

    /** 按用户ID集合批量查询；ids 为空返回空列表。 */
    default List<User> queryUsersByIds(List<String> userIds) {
        return Collections.emptyList();
    }

    /** 按组织ID集合批量查询；ids 为空返回空列表。 */
    default List<Org> queryOrgsByIds(List<String> orgIds) {
        return Collections.emptyList();
    }

    /** 按角色ID集合批量查询；ids 为空返回空列表。 */
    default List<Role> queryRolesByIds(List<String> roleIds) {
        return Collections.emptyList();
    }

    // ==================== 三、人员求解（参与者解析） ====================

    /**
     * 分页查询部门下的人员（成员身份，不含角色继承）——核心方法，实现方覆写。
     *
     * @param includeSubOrgs true = 本部门及全部下级部门（逐层递归，结果按用户去重）；
     *                       false = 仅本部门
     */
    default Page<User> pageQueryUsersOfOrg(String orgId, boolean includeSubOrgs, PageParam pageParam) {
        return Page.empty(pageParam);
    }

    /** 分页查询部门下的人员，默认递归（本部门及全部下级）。 */
    default Page<User> pageQueryUsersOfOrg(String orgId, PageParam pageParam) {
        return pageQueryUsersOfOrg(orgId, true, pageParam);
    }

    /** 部门下的人员全量（可控是否含子部门；default 自动翻页聚合，见接口级约定）。 */
    default List<User> queryUsersOfOrg(String orgId, boolean includeSubOrgs) {
        return collectAll(p -> pageQueryUsersOfOrg(orgId, includeSubOrgs, p));
    }

    /** 部门下的人员全量，默认递归（本部门及全部下级，按用户去重）。 */
    default List<User> queryUsersOfOrg(String orgId) {
        return queryUsersOfOrg(orgId, true);
    }

    /**
     * 分页查询角色下的人员（直挂该角色的用户）——核心方法。
     * <p>按角色编码查询——业务侧（工作流办理人配置、告警规则）引用角色一律存 code。
     */
    default Page<User> pageQueryUsersOfRole(String roleCode, PageParam pageParam) {
        return Page.empty(pageParam);
    }

    /** 角色下的人员全量（default 自动翻页聚合）。 */
    default List<User> queryUsersOfRole(String roleCode) {
        return collectAll(p -> pageQueryUsersOfRole(roleCode, p));
    }

    /**
     * 分页查询部门角色下的人员（经"用户挂组织角色"直挂的用户，用户可不为其部门成员）
     * ——核心方法，实现方覆写。
     *
     * @param includeSubOrgs true = 本部门及全部下级部门挂靠该角色的用户（逐层递归，按用户去重）；
     *                       false = 仅本部门挂靠
     */
    default Page<User> pageQueryUsersOfOrgRole(String orgId, String roleCode, boolean includeSubOrgs, PageParam pageParam) {
        return Page.empty(pageParam);
    }

    /** 分页查询部门角色下的人员，默认递归。 */
    default Page<User> pageQueryUsersOfOrgRole(String orgId, String roleCode, PageParam pageParam) {
        return pageQueryUsersOfOrgRole(orgId, roleCode, true, pageParam);
    }

    /** 部门角色下的人员全量（可控是否含子部门；default 自动翻页聚合）。 */
    default List<User> queryUsersOfOrgRole(String orgId, String roleCode, boolean includeSubOrgs) {
        return collectAll(p -> pageQueryUsersOfOrgRole(orgId, roleCode, includeSubOrgs, p));
    }

    /** 部门角色下的人员全量，默认递归（本部门及全部下级挂靠该角色，按用户去重）。 */
    default List<User> queryUsersOfOrgRole(String orgId, String roleCode) {
        return queryUsersOfOrgRole(orgId, roleCode, true);
    }

    // ==================== 四、部门角色与组织树（层级传递） ====================

    /**
     * 部门挂靠的角色（角色挂组织）——核心方法，实现方覆写。基数小，不分页。
     *
     * @param includeSubOrgs true = 本部门及全部下级部门挂靠的角色；false = 仅直属部门
     */
    default List<OrgRole> queryOrgRoles(String orgId, boolean includeSubOrgs) {
        return Collections.emptyList();
    }

    /** 部门挂靠的角色，默认递归（本部门及全部下级）。 */
    default List<OrgRole> queryOrgRoles(String orgId) {
        return queryOrgRoles(orgId, true);
    }

    /**
     * 部门下的子部门——核心方法，实现方覆写。
     *
     * @param recursive false = 仅直接下级；true = 全部下级（逐层递归，各层级）
     */
    default List<Org> querySubOrgs(String orgId, boolean recursive) {
        return Collections.emptyList();
    }

    /** 部门的全部下级（逐层递归，各层级，不含自身）——默认递归形态。 */
    default List<Org> querySubOrgs(String orgId) {
        return querySubOrgs(orgId, true);
    }

    /** 组织的祖先链（从直接上级到根，不含自身）。 */
    default List<Org> queryAncestorOrgs(String orgId) {
        return Collections.emptyList();
    }

    // ==================== 五、权限查询 ====================

    /**
     * 角色（含组织角色——组织角色即挂靠到组织的角色，权限经角色生效，同一查询）
     * 分配的权限。按角色ID查询（回填场景）。
     */
    default List<Permission> queryPermissionsOfRole(String roleId) {
        return Collections.emptyList();
    }

    /**
     * 用户的生效权限：直挂角色 ∪ 组织挂靠角色 → 角色权限，再 ∪ 用户直授权限。
     * 数据权限按 entity 取 weight 最大者生效（消费方逻辑，见契约规范第 4 节）。
     */
    default List<Permission> queryPermissionsOfUser(String userId) {
        return Collections.emptyList();
    }

    // ==================== 六、决策判断（Check） ====================

    /**
     * 用户是否拥有某角色（按生效角色判断：直挂 ∪ 组织挂靠）。
     * <p>default 由 {@link #getUserRoles(String)} 推导；实现方有更快的
     * 服务端判定（如缓存/专用端点）时应覆写。
     */
    default boolean hasRole(String userId, String roleCode) {
        if (userId == null || roleCode == null) {
            return false;
        }
        UserRoles userRoles = getUserRoles(userId);
        if (userRoles == null) {
            return false;
        }
        return userRoles.getEffectiveRoles().stream()
                .anyMatch(r -> roleCode.equals(r.getCode()));
    }

    /**
     * 用户是否拥有某权限（按生效权限判断，权限以 code 标识）。
     * <p>default 由 {@link #queryPermissionsOfUser(String)} 推导；实现方有更快的
     * 服务端判定时应覆写。
     */
    default boolean hasPermission(String userId, String permissionCode) {
        if (userId == null || permissionCode == null) {
            return false;
        }
        for (Permission permission : queryPermissionsOfUser(userId)) {
            if (permission != null && permissionCode.equals(permission.getCode())) {
                return true;
            }
        }
        return false;
    }

    /**
     * List 全量方法的通用翻页聚合：固定每次 200 条，直到无下一页。
     * 聚合总量超 {@link #MAX_AGGREGATE_SIZE} 抛异常（不静默截断）。
     */
    static <T> List<T> collectAll(Function<PageParam, Page<T>> pageFunction) {
        List<T> all = new ArrayList<>();
        int pageNum = PageParam.DEFAULT_PAGE_NUM;
        while (true) {
            Page<T> page = pageFunction.apply(PageParam.of(pageNum, 200));
            if (page == null || page.getItems() == null || page.getItems().isEmpty()) {
                break;
            }
            all.addAll(page.getItems());
            if (all.size() > MAX_AGGREGATE_SIZE) {
                throw new IllegalStateException(
                        "SecurityProvider 全量聚合超过上限 " + MAX_AGGREGATE_SIZE + "，请改用分页重载（PageParam）");
            }
            if (!page.isHasMore()) {
                break;
            }
            pageNum++;
        }
        return all;
    }
}
