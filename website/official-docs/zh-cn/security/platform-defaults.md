---
title: 平台默认实现
sidebar_label: 平台默认实现
---

# 平台默认实现

本页按层说明安全契约的全部默认实现与 Spring 装配,以及宿主工程替换它们的路径。契约定义在 `geelato-security`;框架层默认实现(快照式 Provider、桥接、刷新协调)在 `geelato-core`;平台层 JDBC 装载实现与统一装配在 `geelato-web-platform`。三者共用包名 `cn.geelato.security`(split package,刻意保留维持 import 一致)。

## 分层总览

| 层 | 模块 | 默认实现 |
| --- | --- | --- |
| 框架层 | `geelato-core` | `DefaultOrgProvider`、`OrgRelationResolver`(包级私有)、`DefaultUserProvider`、`DefaultRoleProvider`、`DefaultUserOrgInfoEnricher`、`DefaultUserExtInfoEnricher`、`BridgeSecurityProvider`、`SecurityDataRefreshCoordinator` |
| 平台层 | `geelato-web-platform` | `DBOrgSnapshotLoader`、`DBUserSnapshotLoader`、`DBRoleSnapshotLoader`、`SecurityProviderConfiguration`(装配) |

默认架构一句话:**Loader 把平台主库数据装配成快照(平台层),Provider 缓存快照对外只读(框架层),装配全部 `@ConditionalOnMissingBean` 可整体替换。**

## 框架层默认实现(geelato-core)

### DefaultOrgProvider

构造注入 `OrgSnapshotLoader`;内部持有 `AtomicReference<OrgSnapshot>` 与 `AtomicReference<OrgRelationResolver>` 两个引用。

| 方法 | 实现 |
| --- | --- |
| `getOrg` | 当前快照 `getOrg` |
| `getAllOrgs` | 当前快照全部组织(值拷贝为 `ArrayList`) |
| `getDeptId` | 委托 `OrgRelationResolver.resolveDeptId` |
| `getCompanyId` | 委托 `OrgRelationResolver.resolveCompanyId` |
| `getCompanyName` | `resolveCompanyOrg` 取公司实体的 `name`,无则 `""` |
| `getCompanyExtendId` | `resolveCompanyOrg` 取公司实体的 `extendId`,无则 `null` |
| `refresh` | `loader.load()` 后原子替换快照引用与关系解析器引用 |

### OrgRelationResolver(包级私有)

基于全量组织索引(`orgId → Org`)的关系解析器,所有解析沿父链上溯,以 visited 集合防环。

| 方法 | 解析规则 |
| --- | --- |
| `resolveDeptId(orgId)` | 组织自身为 `department` 直接返回;否则沿父链找第一个 `department` 祖先;链上无 department 时返回最顶层祖先(链尾节点) |
| `resolveCompanyId(orgId)` | 委托 `resolveCompanyOrg` 取 ID,无则 `""` |
| `resolveCompanyExtendId(orgId)` | 委托 `resolveCompanyOrg` 取 `extendId`,无则 `null` |
| `resolveCompanyOrg(orgId)` | 组织自身为 `company` 直接返回;否则沿父链取第一个 `type=company` 的祖先,**无则回退最顶层祖先**;组织不存在返回 `null` |

这就是[安全模型](model.md)中"公司归属"规则的实现:company 优先、顶层兜底。

### DefaultUserProvider

构造注入 `UserSnapshotLoader` + `UserOrgInfoEnricher`(+ 可选 `UserExtInfoEnricher`);持有 `AtomicReference<UserSnapshot>`。

| 方法 | 实现 |
| --- | --- |
| `getUser` | 当前快照 `getUser` |
| `getAllUsers` | 当前快照全部用户(值拷贝),供 `BridgeSecurityProvider` 反向求解 |
| `getUserByExtendKey` | `normalizeType` 归一化后查快照扩展身份二级索引 |
| `refresh` | `loader.load(userOrgInfoEnricher)` → 若存在 extEnricher 则逐用户 `enrich` → 原子替换快照引用 |

### DefaultRoleProvider

与 `DefaultOrgProvider` 同范式:构造注入 `RoleSnapshotLoader`,`getRole`/`getRoleByCode`/`getOrgRoles` 直查快照,`refresh` 装载后原子替换。

### DefaultUserOrgInfoEnricher

构造注入 `OrgProvider`,依赖组织快照做派生解析。

`enrich(User)`:

1. `orgId` 取 `firstNonBlank(user.orgId, resolveOrgId(user))`;`resolveOrgId` 从 `userOrgs` 中取第一个 `defaultOrg=true` 的挂靠,无则取第一条非空挂靠。
2. 解析出 orgId 后填充:`orgName`、`companyId`、`companyName`、`extendId`、`buId`(= companyId)、`buName`(= companyName);`deptId` 为空时补 `getDeptId`。
3. `defaultOrgId` 同法解析(`defaultOrg=true` 优先)并填充 `defaultOrgName`。

`enrich(UserOrg)`:orgId 非空时补 `name`(空时)、`deptId`、`companyId`、`extendId`,`fullName` 空时取 `name`。

### DefaultUserExtInfoEnricher

缺省态 no-op:上层未提供填充器时,用户 `extendInfo` 保持为空(未绑定扩展信息)。

### BridgeSecurityProvider

`SecurityProvider` 契约的本地库桥接实现:组合 `OrgProvider`/`UserProvider`/`RoleProvider` 三个既有 Provider 的数据面,就地推导契约查询(含人员求解与组织树),不发起任何远程调用——适用于子系统与平台库同库/同网、无需经认证中心的场景。

数据面要求:

| 能力 | 依赖 |
| --- | --- |
| 正向单查/批查 | 三个 Provider 的单查能力即可 |
| 人员求解(部门/角色/部门角色下的人) | `UserProvider.getAllUsers()`(快照式实现天然支持;直连式返回空时人员求解随之返回空) |
| 组织树(子部门/祖先链) | `OrgProvider.getAllOrgs()`;祖先链仅需单查(沿 pid 上溯),无全量要求 |
| 角色相关 | `RoleProvider`;缺省(null)时降级返回空/null |

实现矩阵:

| 契约方法组 | 实现方式 |
| --- | --- |
| 实体单查 | 全实现;`getUserByLoginName` 走扩展键 `loginName`;`getOrgByCode` 全量遍历按 `code` 匹配 |
| 实体批查 | 逐 ID 单查聚合,不存在的跳过 |
| 人员求解 | 内存过滤 `getAllUsers`:部门按成员身份(`userOrgs.orgId` 命中目标部门集合),角色按直挂角色 `code`,部门角色按 `userOrgRoles` 的 `orgId`+`code`(用户可不为该部门成员);结果按用户去重 |
| 分页 | 结果按 `userId` 排序后切片,保证同一数据快照下分页稳定 |
| `querySubOrgs` | 由全量组织构建 pid → 直接下级索引,BFS 逐层展开,visited 防环 |
| `queryAncestorOrgs` | 沿 pid 逐级上溯,不含自身 |
| `queryOrgRoles` | 逐目标部门(可含下级)调 `RoleProvider.getOrgRoles` 聚合 |
| 权限查询 | **不实现**,保持 default 空返回——权限域数据在认证中心侧,请使用认证中心的 `AuthCenterSecurityProvider` |

每次调用即时取 Provider 当前快照推导,不自行缓存,天然与 Provider 的 refresh 语义一致。

### SecurityDataRefreshCoordinator

三快照统一刷新协调器,方法语义与刷新链路见[模型快照与快照加载](snapshot.md);此处仅说明装配:`UserProvider`/`RoleProvider` 均为 `@Autowired(required = false)`,直连式实现自行管理数据面时可缺席。

## 平台层 JDBC 装载(geelato-web-platform)

三个 DB Loader 均构造注入 `@Qualifier("primaryJdbcTemplate")` 的 `JdbcTemplate`,只过滤 `del_status = 0` 的有效记录。

### DBOrgSnapshotLoader

| 项 | 内容 |
| --- | --- |
| 数据源 | `platform_org` |
| 查询 | `select id, pid, name, tenant_code, type, code, extend_id from platform_org where del_status = 0` |
| 装配 | 跳过空 `id` 记录,按装载顺序建 `orgById`(`LinkedHashMap`),经 `OrgSnapshot.from` 包装发布 |

### DBUserSnapshotLoader

四段查询,逐段装配到 `userById`:

| 段 | 数据源 | 装配内容 |
| --- | --- | --- |
| 1. 用户基础 | `platform_user where del_status = 0`:`id`/`login_name`/`name`/`tenant_code`/`weixin_unionId`/`weixin_work_userId` | 用户对象;同时建 `loginName`/`weixinUnionId`/`weixinWorkUserId` 三个扩展身份索引 |
| 2. 直挂角色 | `platform_role` join `platform_role_r_user`(两表 `del_status = 0`) | `UserRole`(id/code/name/type/tenantCode) |
| 3. 组织挂靠角色 | `platform_user_r_org_role` join `platform_role`(两表 `del_status = 0`) | `UserOrgRole`(角色字段 + `org_id`) |
| 4. 组织挂靠 | `platform_org_r_user where del_status = 0`:`user_id`/`org_id`/`org_name`/`default_org` | `UserOrg`;`default_org` 按 `"1"` 或 `true` 判定;逐条经 `userOrgInfoEnricher.enrich(uo)` 补齐;`defaultOrg=true` 时回填 `user.orgId`/`user.defaultOrgId` |

收尾:逐用户 `enrich(user)` 补派生字段;`userRoles`/`userOrgs`/`userOrgRoles` 为 null 时置空列表、否则 `List.copyOf` 不可变化;最后 `UserSnapshot.from(userById, extendIndex)` 发布。

### DBRoleSnapshotLoader

| 项 | 内容 |
| --- | --- |
| 角色实体 | `SELECT id, code, name, type, tenant_code FROM platform_role WHERE del_status = 0` → `roleById`;字段经 `trimToNull` 规整 |
| 组织挂靠角色 | `SELECT org_id, role_id FROM platform_org_r_role WHERE del_status = 0` → 按角色 ID 复制出 `OrgRole`(copyRole)并记 `orgId`;角色不存在的关联跳过 |
| 发布 | `RoleSnapshot.from(roleById, orgRolesByOrgId)` |

## Spring 装配:SecurityProviderConfiguration

`geelato-web-platform` 的 `cn.geelato.web.platform.boot.SecurityProviderConfiguration` 提供 10 个默认 bean,**全部 `@ConditionalOnMissingBean`**——宿主工程定义同类型 bean 时默认装配自动让位:

| Bean | 默认类 | 依赖 |
| --- | --- | --- |
| `OrgSnapshotLoader` | `DBOrgSnapshotLoader` | `primaryJdbcTemplate` |
| `OrgProvider` | `DefaultOrgProvider` | orgSnapshotLoader |
| `UserOrgInfoEnricher` | `DefaultUserOrgInfoEnricher` | orgProvider |
| `UserExtInfoEnricher` | `DefaultUserExtInfoEnricher` | — |
| `UserSnapshotLoader` | `DBUserSnapshotLoader` | `primaryJdbcTemplate` |
| `UserProvider` | `DefaultUserProvider` | userSnapshotLoader + userOrgInfoEnricher + userExtInfoEnricher |
| `RoleSnapshotLoader` | `DBRoleSnapshotLoader` | `primaryJdbcTemplate` |
| `RoleProvider` | `DefaultRoleProvider` | roleSnapshotLoader |
| `SecurityProvider` | `BridgeSecurityProvider` | orgProvider + userProvider + roleProvider |
| `SecurityDataRefreshCoordinator` | `SecurityDataRefreshCoordinator` | orgProvider + userProvider + roleProvider |

说明:

- 平台应用不依赖 `geelato-auth-client`,无远程实现竞争;接入认证中心的子系统由 auth-client 自动装配 `AuthCenterSecurityProvider`(走开放 API,不在本仓库),与本 bean 互不干扰。
- 启动时 `SecurityDataRefreshCoordinator` 的 `@PostConstruct` 完成三快照首次装载;运行期由 `OrgController.refreshOrg()`/`UserController.refreshUser()` 在目录变更后触发。

## 如何替换

三种替换方式,按改动范围从小到大:

### 方式一:替换 Enricher(最小改动)

用户快照本身不完整,或组织归属需要额外推导:只需提供 `UserOrgInfoEnricher`(组织派生)或 `UserExtInfoEnricher`(`extendInfo` 填充)的自定义 bean,不动整套 Provider。适合为 `extendInfo` 接入业务绑定数据(git 绑定等约定键)。

### 方式二:保留 Provider,替换 Loader(推荐)

认可默认 Provider 的"快照 + 只读查询"模型,但数据来源不是平台主库:只需提供 `OrgSnapshotLoader`/`UserSnapshotLoader`/`RoleSnapshotLoader` 的自定义 bean。仍可复用 `Default*Provider`、`BridgeSecurityProvider`、`SecurityDataRefreshCoordinator`,快照数据可来自外部服务、配置中心、Redis、聚合接口。这通常是更稳妥的方式。

### 方式三:直接替换 Provider(整套数据面不适用)

已具备完整的组织/用户/角色访问模型(外部 IAM、组织中心 API、不使用快照模型):直接提供自定义 `OrgProvider`/`UserProvider`/`RoleProvider`(或 `SecurityProvider`)bean。注意替换 `UserProvider` 时扩展身份映射(`getUserByExtendKey`)必须可用,否则微信/企业微信等扩展键认证失效。

### 最低实现方法清单

自定义 `UserProvider` 最低限度实现:`getUser(String)`、`getUserByExtendKey(String, String)`、`refresh()`。若返回的 `User` 已带 `userRoles`/`userOrgs`/`userOrgRoles`,三个关系查询的 default 实现可直接复用。

自定义 `OrgProvider` 最低限度实现:`getOrg(String)`、`getDeptId(String)`、`getCompanyId(String)`、`refresh()`。运行时不仅需要组织名称,还需要部门与公司/BU 派生——这部分关系若处理不完整,会直接影响认证后用户的组织维度上下文。

### 语义稳定要求

- `refresh()` 能够重新加载最新快照,不要实现成有副作用的重事务流程。
- 组织补全(`getDeptId`/`getCompanyId`/`getCompanyName`)语义保持稳定,消费方包括鉴权后的组织维度回填与快照装载。
- `getUserByExtendKey` 的三类扩展身份映射(`loginName`/`weixinUnionId`/`weixinWorkUserId`)保持可用。

## 总结

- 默认实现分两层:框架层(`geelato-core`)提供快照式 Provider、关系解析、桥接查询与刷新协调;平台层(`geelato-web-platform`)提供三个 JDBC Loader 与统一装配。
- 公司归属解析收敛在 `OrgRelationResolver`:沿父链第一个 `type=company` 祖先,无则回退最顶层祖先。
- 装配全部 `@ConditionalOnMissingBean`:替换 Enricher → 替换 Loader → 整换 Provider,改动范围逐级放大;Loader 替换是最推荐的接入方式。

## 推荐继续阅读

- [抽象装载类](loader.md)——替换 Loader 时实现的接口
- [模型快照与快照加载](snapshot.md)——刷新链路与不可变语义
- [权限接入](../authentication/permission-integration.md)——AuthServer 侧的统一权限管理
