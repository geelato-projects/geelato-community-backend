package cn.geelato.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * 安全契约读侧帮助方法：四实体（组织/用户/角色/权限）与四类挂靠关系的按需单点查询。
 * <p>
 * 方法分六组（对齐业界标杆：Zanzibar/K8s SubjectAccessReview 的决策类、Casbin/Graph 的
 * 层级传递、Graph $batch 的批查、Keycloak/Okta 的分页）：
 * <ol>
 *   <li>实体单查；</li>
 *   <li>实体批查（批量按 ID，减少网络 N+1）；</li>
 *   <li>关系查询（组织下的用户/角色、组织角色下的用户、角色下的用户、用户的角色）；</li>
 *   <li>权限查询（角色的权限、用户的生效权限）；</li>
 *   <li>组织树（祖先链/全部下级，支撑"部门及下级"数据权限场景）；</li>
 *   <li>决策判断（Check：hasRole/hasPermission，业务代码最高频入口）。</li>
 * </ol>
 * 约定：
 * <ul>
 *   <li>appCode/tenantCode 由实现方绑定（配置或客户端身份携带），契约方法不携带租户/子系统参数；</li>
 *   <li>所有方法均为 default 空实现，接入系统按需覆写部分方法即可接入契约；
 *       认证中心的 geelato-auth-client 提供走开放 API 的全量默认实现；</li>
 *   <li>分页 page* 为核心抽象，list* 全量为 default 自动翻页聚合（固定每次 200 条），
 *       聚合总量超 {@link #MAX_AGGREGATE_SIZE} 抛异常——宁可失败也不静默截断，
 *       大基数场景请改用分页方法；</li>
 *   <li>与 {@link UserProvider}/{@link OrgProvider}（快照式全量装载）并存：
 *       本接口面向按需单点查询，两者各司其职。</li>
 * </ul>
 */
public interface SecurityContractReader {

    /**
     * list* 全量聚合的安全上限：超过即抛 IllegalStateException，
     * 防止大基数场景（如全公司用户）打爆内存与网络。
     */
    int MAX_AGGREGATE_SIZE = 10_000;

    // ==================== 一、实体单查 ====================

    /**
     * 根据用户ID获取用户信息。
     *
     * @return 用户；不存在或无权限可见时返回 null
     */
    default User getUserById(String userId) {
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
     * 根据角色编码获取角色（业务代码与数据权限规则引用角色一律用 code，不用 ID）。
     *
     * @return 角色；不存在或无权限可见时返回 null
     */
    default Role getRoleByCode(String roleCode) {
        return null;
    }

    // ==================== 二、实体批查（减少网络 N+1） ====================

    /** 按用户ID集合批量查询；ids 为空返回空列表。 */
    default List<User> listUsersByIds(List<String> userIds) {
        return Collections.emptyList();
    }

    /** 按组织ID集合批量查询；ids 为空返回空列表。 */
    default List<Org> listOrgsByIds(List<String> orgIds) {
        return Collections.emptyList();
    }

    /** 按角色ID集合批量查询；ids 为空返回空列表。 */
    default List<Role> listRolesByIds(List<String> roleIds) {
        return Collections.emptyList();
    }

    // ==================== 三、关系查询 ====================

    /**
     * 分页查询组织下的用户（成员身份，不含角色继承，不含下级部门）。
     */
    default Page<User> pageUsersOfOrg(String orgId, PageParam pageParam) {
        return Page.empty(pageParam);
    }

    /** 组织下的用户全量（自动翻页聚合，见接口级约定）。 */
    default List<User> listUsersOfOrg(String orgId) {
        return collectAll(p -> pageUsersOfOrg(orgId, p));
    }

    /** 分页查询角色下的用户（直挂该角色的用户）。 */
    default Page<User> pageUsersOfRole(String roleId, PageParam pageParam) {
        return Page.empty(pageParam);
    }

    /** 角色下的用户全量（直挂，自动翻页聚合）。 */
    default List<User> listUsersOfRole(String roleId) {
        return collectAll(p -> pageUsersOfRole(roleId, p));
    }

    /**
     * 分页查询组织角色下的用户（经"用户挂组织角色"直挂的用户，用户可不为其部门成员）。
     */
    default Page<User> pageUsersOfOrgRole(String orgId, String roleId, PageParam pageParam) {
        return Page.empty(pageParam);
    }

    /** 组织角色下的用户全量（自动翻页聚合）。 */
    default List<User> listUsersOfOrgRole(String orgId, String roleId) {
        return collectAll(p -> pageUsersOfOrgRole(orgId, roleId, p));
    }

    /**
     * 本部门及全部下级部门的用户（不含角色继承；层级传递语义，对应数据权限
     * "部门及下级"高频场景）。结果按用户去重。
     */
    default List<User> listUsersOfOrgTree(String orgId) {
        return Collections.emptyList();
    }

    /**
     * 组织挂靠的角色（角色挂组织）。基数小，不分页。
     */
    default List<OrgRole> listOrgRoles(String orgId) {
        return Collections.emptyList();
    }

    /**
     * 用户的角色，区分直挂角色（userRoles）与组织挂靠角色（userOrgRoles，含来源部门）。
     * 生效角色（并集去重）经 {@link UserRoles#getEffectiveRoles()} 获取。
     */
    default UserRoles getUserRoles(String userId) {
        return new UserRoles();
    }

    // ==================== 四、权限查询 ====================

    /**
     * 角色（含组织角色——组织角色即挂靠到组织的角色，权限经角色生效，同一查询）
     * 分配的权限。
     */
    default List<Permission> listPermissionsOfRole(String roleId) {
        return Collections.emptyList();
    }

    /**
     * 用户的生效权限：直挂角色 ∪ 组织挂靠角色 → 角色权限，再 ∪ 用户直授权限。
     * 数据权限按 entity 取 weight 最大者生效（消费方逻辑，见契约规范第 4 节）。
     */
    default List<Permission> listPermissionsOfUser(String userId) {
        return Collections.emptyList();
    }

    // ==================== 五、组织树（层级传递） ====================

    /** 组织的祖先链（从直接上级到根，不含自身）。 */
    default List<Org> listAncestorOrgs(String orgId) {
        return Collections.emptyList();
    }

    /** 组织的全部下级（不含自身，含各层级）。 */
    default List<Org> listDescendantOrgs(String orgId) {
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
     * <p>default 由 {@link #listPermissionsOfUser(String)} 推导；实现方有更快的
     * 服务端判定时应覆写。
     */
    default boolean hasPermission(String userId, String permissionCode) {
        if (userId == null || permissionCode == null) {
            return false;
        }
        for (Permission permission : listPermissionsOfUser(userId)) {
            if (permission != null && permissionCode.equals(permission.getCode())) {
                return true;
            }
        }
        return false;
    }

    /**
     * list* 全量方法的通用翻页聚合：固定每次 200 条，直到无下一页。
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
                        "SecurityContractReader 全量聚合超过上限 " + MAX_AGGREGATE_SIZE + "，请改用分页方法（page*）");
            }
            if (!page.isHasMore()) {
                break;
            }
            pageNum++;
        }
        return all;
    }
}
