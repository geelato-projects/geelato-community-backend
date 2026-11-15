---
title: Provider 契约
sidebar_label: Provider 契约
---

# Provider 契约

本页逐方法说明 `geelato-security` 的四个 Provider 接口与分页契约。Provider 是安全数据的读取边界:消费方只依赖这些接口,不依赖具体实现;所有方法只定义读取能力,不含任何存储与网络细节。

## 总览

| 接口 | 范式 | 职责 |
| --- | --- | --- |
| `OrgProvider` | 快照式全量 + refresh | 组织只读查询与组织关系解析(部门/公司/BU) |
| `UserProvider` | 快照式全量 + refresh | 用户只读查询、关系读取、扩展身份查询 |
| `RoleProvider` | 快照式全量 + refresh | 角色只读查询、组织挂靠角色查询 |
| `SecurityProvider` | 按需单点查询 | 四实体与四类挂靠关系的单查/批查/人员求解/层级传递/权限查询/决策判断 |

前三个面向快照式全量装载(与 [Loader](loader.md)/[Snapshot](snapshot.md) 配套);`SecurityProvider` 面向按需单点查询,供工作流办理人求解、规则/异常告警的参与者解析、数据权限判定等场景消费。两者并存、各司其职。

## OrgProvider

| 方法 | 签名 | 契约语义 |
| --- | --- | --- |
| `getOrg` | `Org getOrg(String orgId)` | 按 ID 查组织,不存在返回 `null`(抽象方法) |
| `getAllOrgs` | `List<Org> getAllOrgs()` | 全量组织;default 返回空列表,快照式实现应覆写 |
| `containsOrg` | `boolean containsOrg(String orgId)` | 组织是否存在;default 由 `getOrg != null` 推导 |
| `getOrgName` | `String getOrgName(String orgId)` | 组织名称;default 由 `getOrg` 取名,无则返回 `""` |
| `getDeptId` | `String getDeptId(String orgId)` | 组织 → 所属部门 ID 的关系解析入口(抽象方法,解析规则由实现定义) |
| `getCompanyId` | `String getCompanyId(String orgId)` | 组织 → 所属公司 ID 的关系解析入口(抽象方法) |
| `getCompanyName` | `String getCompanyName(String orgId)` | 所属公司名称;default 经 `getCompanyId` 取名。链上无 company 时与 `getCompanyId` 一致,取最顶层祖先 |
| `getCompanyExtendId` | `String getCompanyExtendId(String orgId)` | 所属公司的 `extendId`,与 `getCompanyId` 同源;无 company 时取最顶层祖先的 |
| `getBuId` | `String getBuId(String orgId)` | 业务单元 ID;default 即 `getCompanyId` |
| `refresh` | `void refresh()` | 重载快照(抽象方法) |

`getDeptId`/`getCompanyId` 不只是字典查询,而是组织关系解析入口:从树关系推导部门与公司。默认实现的解析算法(沿父链第一个 `department`/`company`、无则顶层兜底)见[平台默认实现](platform-defaults.md)。

## UserProvider

| 方法 | 签名 | 契约语义 |
| --- | --- | --- |
| `getUser` | `User getUser(String userId)` | 按 ID 查用户(抽象方法) |
| `getUserName` | `String getUserName(String userId)` | 用户名称;default 由 `getUser` 取名,无则返回 `""` |
| `getAllUsers` | `List<User> getAllUsers()` | 全量用户,含 `userRoles`/`userOrgs`/`userOrgRoles` 挂靠关系,供 `SecurityProvider` 桥接实现求解部门/角色下的人员等反向查询。快照式实现应覆写;直连式实现可不支持(返回空列表,桥接的人员求解随之返回空) |
| `containsUser` | `boolean containsUser(String userId)` | 用户是否存在;default 由 `getUser != null` 推导 |
| `getUserById` | `User getUserById(String userId)` | 同 `getUser`;default 直接委托 |
| `getUserByExtendKey` | `User getUserByExtendKey(String extendKey, String type)` | 按扩展身份键查用户(抽象方法),`type` 经 `normalizeType` 归一化 |
| `getUserRoles` | `List<UserRole> getUserRoles(String userId)` | 直挂角色;default 从 `User.userRoles` 聚合取,无则空列表 |
| `getUserOrgs` | `List<UserOrg> getUserOrgs(String userId)` | 组织挂靠;default 从 `User.userOrgs` 聚合取 |
| `getUserOrgRoles` | `List<UserOrgRole> getUserOrgRoles(String userId)` | 组织挂靠角色;default 从 `User.userOrgRoles` 聚合取 |
| `normalizeType` | `String normalizeType(String type)` | 扩展键类型归一化,见下表 |
| `refresh` | `void refresh()` | 重载快照(抽象方法) |

`normalizeType` 归一化规则(忽略大小写):

| 入参 | 归一化为 |
| --- | --- |
| `loginName` / `login_name` | `loginName` |
| `weixinUnionId` / `weixin_unionId` / `unionId` | `weixinUnionId` |
| `weixinWorkUserId` / `weixin_work_userId` / `workUserId` | `weixinWorkUserId` |
| 其他 | 原样返回 |

这也是运行时鉴权链路可以直接使用 `WeixinUnionId`、`WeixinWorkUserId` 这类扩展身份入口的原因(见[认证鉴权](../authentication/security-authentication.md))。

## RoleProvider

| 方法 | 签名 | 契约语义 |
| --- | --- | --- |
| `getRole` | `Role getRole(String roleId)` | 按角色 ID 查询,不存在返回 `null`(抽象方法) |
| `getRoleByCode` | `Role getRoleByCode(String roleCode)` | 按角色编码查询;业务引用角色一律用 `code`(抽象方法) |
| `getOrgRoles` | `List<OrgRole> getOrgRoles(String orgId)` | 组织挂靠的角色(部门下的角色,直属部门,不沿组织树向上递归)。基数小,不分页;default 返回空列表 |
| `refresh` | `void refresh()` | 重载快照(抽象方法) |

## SecurityProvider

安全数据提供者:四实体(组织/用户/角色/权限)与四类挂靠关系的按需单点查询。方法分六组,全部为 default 空实现,接入系统按需覆写部分方法即可接入契约。常量 `MAX_AGGREGATE_SIZE = 10_000` 为 List 全量聚合的安全上限,超过即抛 `IllegalStateException`——宁可失败也不静默截断,大基数场景请改用分页重载。

### 一、实体单查(get)

| 方法 | 契约语义 |
| --- | --- |
| `getUserById(String userId)` | 按用户 ID 查用户;不存在或无权限可见时返回 `null` |
| `getUserByLoginName(String loginName)` | 按登录名查用户(通知/告警场景按登录名找人的入口) |
| `getOrgById(String orgId)` | 按组织 ID 查组织 |
| `getOrgByCode(String orgCode)` | 按组织编码查组织 |
| `getRoleById(String roleId)` | 按角色 ID 查角色(回填场景:手上已有角色 ID,如 `getUserRoles`/`queryOrgRoles` 的结果) |
| `getRoleByCode(String roleCode)` | 按角色编码查角色(业务代码与数据权限规则引用角色一律用 code) |
| `getUserRoles(String userId)` | 用户的角色,返回 `UserRoles` 聚合,区分直挂角色与组织挂靠角色(含来源部门);生效角色经 `UserRoles.getEffectiveRoles()` 获取 |
| `getUserOrgs(String userId)` | 用户挂靠的组织(成员身份,多部门挂靠一并返回;与角色挂靠无关) |

### 二、实体批查(query,减少网络 N+1)

| 方法 | 契约语义 |
| --- | --- |
| `queryUsersByIds(List<String> userIds)` | 按用户 ID 集合批量查询;ids 为空返回空列表 |
| `queryOrgsByIds(List<String> orgIds)` | 按组织 ID 集合批量查询 |
| `queryRolesByIds(List<String> roleIds)` | 按角色 ID 集合批量查询 |

### 三、人员求解(参与者解析)

三个维度(部门/角色/部门角色)的人员求解,是工作流与告警的高频入口。命名规律:带全部参数的 `pageQuery*` 为核心抽象(实现方覆写);无标志位的重载为 default 推导(默认递归);`query*` 全量为 default 自动翻页聚合。

| 方法 | 契约语义 |
| --- | --- |
| `pageQueryUsersOfOrg(String orgId, boolean includeSubOrgs, PageParam pageParam)` | 分页查询部门下的人员(成员身份,不含角色继承)。`includeSubOrgs=true` 含本部门及全部下级(逐层递归,结果按用户去重);`false` 仅本部门。核心方法,实现方覆写 |
| `pageQueryUsersOfOrg(String orgId, PageParam pageParam)` | 同上,默认递归 |
| `queryUsersOfOrg(String orgId, boolean includeSubOrgs)` | 部门下的人员全量;default 自动翻页聚合 |
| `queryUsersOfOrg(String orgId)` | 同上,默认递归(本部门及全部下级,按用户去重) |
| `pageQueryUsersOfRole(String roleCode, PageParam pageParam)` | 分页查询直挂该角色的用户;按角色编码查询——业务侧(工作流办理人配置、告警规则)引用角色一律存 code。核心方法 |
| `queryUsersOfRole(String roleCode)` | 角色下的人员全量;default 自动翻页聚合 |
| `pageQueryUsersOfOrgRole(String orgId, String roleCode, boolean includeSubOrgs, PageParam pageParam)` | 分页查询部门角色下的人员(经"用户挂组织角色"直挂的用户,用户可不为其部门成员)。核心方法 |
| `pageQueryUsersOfOrgRole(String orgId, String roleCode, PageParam pageParam)` | 同上,默认递归 |
| `queryUsersOfOrgRole(String orgId, String roleCode, boolean includeSubOrgs)` | 部门角色下的人员全量;default 自动翻页聚合 |
| `queryUsersOfOrgRole(String orgId, String roleCode)` | 同上,默认递归 |

全量聚合由静态方法 `collectAll` 实现:固定每次 200 条翻页,直到无下一页;聚合总量超 `MAX_AGGREGATE_SIZE` 抛 `IllegalStateException`。

### 四、部门角色与组织树(层级传递)

| 方法 | 契约语义 |
| --- | --- |
| `queryOrgRoles(String orgId, boolean includeSubOrgs)` | 部门挂靠的角色(角色挂组织)。`true` 含本部门及全部下级部门挂靠的角色;`false` 仅直属部门。核心方法,基数小不分页 |
| `queryOrgRoles(String orgId)` | 同上,默认递归 |
| `querySubOrgs(String orgId, boolean recursive)` | 部门下的子部门。`false` 仅直接下级;`true` 全部下级(逐层递归,各层级)。核心方法 |
| `querySubOrgs(String orgId)` | 全部下级,不含自身 |
| `queryAncestorOrgs(String orgId)` | 组织的祖先链,从直接上级到根,不含自身 |

### 五、权限查询

| 方法 | 契约语义 |
| --- | --- |
| `queryPermissionsOfRole(String roleId)` | 角色分配的权限。组织角色即挂靠到组织的角色,权限经角色生效,同一查询;按角色 ID 查询(回填场景) |
| `queryPermissionsOfUser(String userId)` | 用户的生效权限:直挂角色 ∪ 组织挂靠角色 → 角色权限,再 ∪ 用户直授权限。数据权限按 entity 取 weight 最大者生效(消费方逻辑) |

### 六、决策判断(Check)

| 方法 | 契约语义 |
| --- | --- |
| `hasRole(String userId, String roleCode)` | 用户是否拥有某角色,按生效角色(直挂 ∪ 组织挂靠)判断;default 由 `getUserRoles` 推导,实现方有更快的判定(缓存/专用端点)时应覆写 |
| `hasPermission(String userId, String permissionCode)` | 用户是否拥有某权限(权限以 code 标识);default 由 `queryPermissionsOfUser` 推导,有更快判定时应覆写 |

## 命名与参数约定

命名约定:

| 前缀 | 含义 |
| --- | --- |
| `get*` | 单实体查询(返回单对象或单用户的聚合视图) |
| `query*` | 列表全量查询 |
| `pageQuery*` | 分页查询 |

参数约定:

- `appCode`/`tenantCode` 由实现方绑定(配置或客户端身份携带),契约方法不携带租户/子系统参数。
- 角色引用一律用 `code`(跨环境稳定);仅实体批查(`queryRolesByIds`)与角色权限查询(`queryPermissionsOfRole`)按 ID——它们服务于"手上已有 ID 列表/角色对象"的回填场景,不作业务引用入口。
- 组织引用一律用 `orgId`;部门相关的人员/角色求解均带 `includeSubOrgs` 参数(`true` = 本部门及全部下级,逐层递归)。**不带该参数的重载默认递归含全部子部门**;`querySubOrgs(orgId, recursive)` 中 `recursive` 控制直接下级/全部下级。
- 所有方法均为 default 空实现,按需覆写即可接入契约。

## 分页契约:Page 与 PageParam

### PageParam

| 成员 | 意义 |
| --- | --- |
| `DEFAULT_PAGE_NUM = 1` / `DEFAULT_PAGE_SIZE = 20` | 默认页码与页大小 |
| `MAX_PAGE_SIZE = 500` | 页大小上限 |
| `pageNum` | 页码,从 1 起 |
| `pageSize` | 页大小,`of()` 构造时钳制到 `[1, 500]` |
| `of(int pageNum, int pageSize)` | 构造方法,自动钳制 |
| `getOffset()` | 查询偏移量,`(pageNum - 1) * pageSize` |

### Page

| 成员 | 意义 |
| --- | --- |
| `items` | 当前页数据(null 时以空列表替代) |
| `total` | 总条数 |
| `pageNum` / `pageSize` | 页码与页大小 |
| `hasMore` | 是否还有下一页(全量聚合与翻页终止的判定依据) |
| `of(items, total, pageNum, pageSize, hasMore)` | 构造方法 |
| `empty(PageParam)` | 空页,`pageParam` 为 null 时用默认参数 |

## 总结

- 前三个 Provider 是快照式只读边界(全量装载 + refresh),`SecurityProvider` 是按需单点查询边界(六组方法),两者并存各司其职。
- 命名约定 `get*`/`query*`/`pageQuery*` 对应单查/全量/分页;角色引用一律 code,组织引用一律 orgId;租户与应用由实现方绑定。
- 全量聚合有硬上限(`MAX_AGGREGATE_SIZE`),超限抛异常不静默截断;大基数场景用分页重载。
- 默认平台实现的解析算法与装配见[平台默认实现](platform-defaults.md)。

## 推荐继续阅读

- [安全模型](model.md)——返回的对象长什么样
- [抽象装载类](loader.md)——快照从哪里来
- [平台默认实现](platform-defaults.md)——开箱即用的实现与替换方式
