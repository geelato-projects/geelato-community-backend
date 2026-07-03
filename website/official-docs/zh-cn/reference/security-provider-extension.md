# 安全 Provider 扩展

这篇文档说明 `geelato-security` 中两个非常关键的安全抽象：

- `UserProvider`
- `OrgProvider`

它们为什么存在、默认实现如何工作，以及宿主工程如何基于这两个接口替换默认安全数据来源。

## 这两个抽象解决什么问题

平台运行时在做认证、组织补全、在线态展示和用户信息查询时，并不希望把“用户/组织数据从哪里来”硬编码死在拦截器或控制器里。

因此框架把这部分能力抽象成：

- `UserProvider`
- `OrgProvider`

这样消费方只依赖“读取用户/组织信息”的统一接口，而不依赖具体实现到底来自：

- 主库快照
- 外部 IAM
- 企业微信
- 组织中心
- 缓存中台
- 组合聚合服务

## `UserProvider` 负责什么

`UserProvider` 面向消费方暴露的是“用户只读查询能力”。

当前核心能力包括：

- `getUser(String userId)`
- `getUserById(String userId)`
- `getUserByExtendKey(String extendKey, String type)`
- `getUserRoles(String userId)`
- `getUserOrgs(String userId)`
- `refresh()`

同时它还提供了若干默认辅助方法，例如：

- `getUserName(...)`
- `containsUser(...)`
- `normalizeType(...)`

其中 `normalizeType(...)` 当前已经把几类常见扩展键类型统一归一化，例如：

- `loginName`
- `weixinUnionId`
- `weixinWorkUserId`

这也是为什么运行时鉴权链路可以直接使用：

- `WeixinUnionId`
- `WeixinWorkUserId`

这类扩展身份入口。

## `OrgProvider` 负责什么

`OrgProvider` 面向消费方暴露的是“组织只读查询能力”。

当前核心能力包括：

- `getOrg(String orgId)`
- `getOrgName(String orgId)`
- `getDeptId(String orgId)`
- `getCompanyId(String orgId)`
- `getBuId(String orgId)`
- `refresh()`

这里的重点不只是“查到组织对象”，还包括：

- 从组织关系中推导部门
- 从组织关系中推导公司 / 业务单元

因此它本质上也是一个：

- 组织关系解析入口

而不仅仅是一个简单字典查询接口。

## 运行时哪些地方依赖它们

这两个 Provider 当前是安全链路里的关键依赖。

典型使用点包括：

- `DefaultSecurityInterceptor`
- `InterceptorConfiguration`
- `SecurityDataRefreshCoordinator`
- `User.setupOrgInfo(...)`

其中：

- `DefaultSecurityInterceptor` 依赖 `UserProvider` 处理扩展键认证
- `DefaultSecurityInterceptor` 依赖 `OrgProvider` 给用户补组织维度信息
- 安全数据刷新协调器依赖两者做统一快照刷新

因此它们不是“可有可无的辅助接口”，而是：

- 安全链路的数据提供边界

## 默认实现怎么工作

当前默认装配入口位于：

- `SecurityProviderConfiguration`

它通过：

- `@ConditionalOnMissingBean(OrgProvider.class)`
- `@ConditionalOnMissingBean(UserProvider.class)`

提供默认实现：

- `DefaultOrgProvider`
- `DefaultUserProvider`

这说明它们天生就是可替换扩展点。

### 默认 `OrgProvider`

默认实现：

- `DefaultOrgProvider`

它依赖：

- `OrgSnapshotLoader`

并把加载结果缓存在：

- `AtomicReference<OrgSnapshot>`

同时还维护：

- `OrgRelationResolver`

用于快速解析：

- 部门 ID
- 公司 ID
- 业务单元 ID

### 默认 `UserProvider`

默认实现：

- `DefaultUserProvider`

它依赖：

- `UserSnapshotLoader`
- `UserOrgInfoEnricher`

并把用户快照缓存在：

- `AtomicReference<UserSnapshot>`

调用 `refresh()` 时，会重新加载快照并刷新本地只读视图。

## 默认数据来源是什么

当前默认数据来源并不是直接在 Provider 里写 SQL，而是再往下一层抽象为：

- `OrgSnapshotLoader`
- `UserSnapshotLoader`

在 `SecurityProviderConfiguration` 中，如果宿主工程没有自定义加载器，默认会创建：

- `JdbcOrgSnapshotLoader`
- `JdbcUserSnapshotLoader`

也就是说，默认架构是：

1. Loader 负责把组织/用户数据加载成快照
2. Provider 负责对外提供只读访问接口
3. 上层安全链路只消费 Provider，不直接关心底层来源

## 如何扩展

### 方式一：直接替换 `OrgProvider` / `UserProvider`

如果你已经有自己完整的组织和用户访问模型，最直接的方式就是在宿主工程里提供自己的：

- `OrgProvider`
- `UserProvider`

因为默认装配是 `@ConditionalOnMissingBean`，所以你自己的 Bean 一旦存在，默认实现就不会再创建。

这适合：

- 用户和组织来自外部 IAM
- 需要直接接组织中心 API
- 不想使用默认快照模型

### 方式二：保留 Provider，替换 Loader

如果你认可默认 Provider 的“快照 + 只读查询”模型，但不想用默认 JDBC 来源，则更推荐只替换：

- `OrgSnapshotLoader`
- `UserSnapshotLoader`

这样你仍可复用：

- `DefaultOrgProvider`
- `DefaultUserProvider`
- `SecurityDataRefreshCoordinator`

但底层快照数据可以来自：

- 外部服务
- 配置中心
- Redis
- 聚合接口

这通常是更稳妥的方式。

### 方式三：补充组织信息增强器

对于用户组织信息补全，当前还预留了：

- `UserOrgInfoEnricher`

默认实现是：

- `DefaultUserOrgInfoEnricher`

如果你的用户快照本身不完整，或者组织归属需要额外推导，也可以只替换这一层，而不是整套 `UserProvider`。

## 开发者应该实现哪些方法

### 自定义 `UserProvider`

最低限度应实现：

- `getUser(String userId)`
- `getUserByExtendKey(String extendKey, String type)`
- `refresh()`

如果你返回的 `User` 已经带有：

- `userRoles`
- `userOrgs`

那么默认的：

- `getUserRoles(...)`
- `getUserOrgs(...)`

就可以直接复用。

### 自定义 `OrgProvider`

最低限度应实现：

- `getOrg(String orgId)`
- `getDeptId(String orgId)`
- `getCompanyId(String orgId)`
- `refresh()`

因为运行时不只需要组织名称，还需要：

- 部门
- 公司 / BU

这部分关系若处理不完整，会直接影响认证后用户组织维度上下文。

## 刷新机制怎么理解

当前框架里提供了：

- `SecurityDataRefreshCoordinator`

它会在 `@PostConstruct` 时调用：

- `refreshAll()`

后续也支持分别调用：

- `refreshOrg()`
- `refreshUser()`

因此当你扩展 Provider 或 Loader 时，`refresh()` 的语义要保持稳定：

- 能够重新加载最新快照
- 不要把 `refresh()` 实现成有副作用的重事务流程

## 与认证鉴权链路的关系

`UserProvider` 和 `OrgProvider` 并不是单纯供业务页面查询的接口，它们直接影响：

- 扩展键认证
- 组织信息补全
- 当前用户组织维度上下文

特别是：

- `UserProvider.getUserByExtendKey(...)`

直接服务于：

- `WeixinUnionId`
- `WeixinWorkUserId`

这类认证入口。

因此如果宿主工程替换了 `UserProvider`，要特别保证这些扩展身份映射仍然可用。

## 推荐扩展策略

更推荐的顺序通常是：

1. 先判断你要改的是“底层数据来源”还是“整个 Provider 行为”
2. 如果只是来源变化，优先替换 `OrgSnapshotLoader` / `UserSnapshotLoader`
3. 如果连快照模型都不适用，再直接替换 `OrgProvider` / `UserProvider`
4. 保持 `refresh()`、组织补全和扩展键查询语义稳定

## 总结

`UserProvider` 和 `OrgProvider` 当前就是框架安全链路里最重要的两个可扩展 Provider 抽象：

- `UserProvider` 负责用户、角色、组织归属和扩展键映射
- `OrgProvider` 负责组织读取和组织关系解析
- 默认实现采用“Loader -> Snapshot -> Provider”模型
- 默认装配受 `@ConditionalOnMissingBean` 保护，适合宿主工程覆盖

因此它们非常适合作为：

- 统一身份源接入点
- 组织中心对接点
- 外部 IAM / 企业微信 / 自研账号体系的安全适配边界

## 推荐继续阅读

- [认证鉴权](../authentication/security-authentication.md)
- [覆盖默认实现](override-default-implementations.md)
