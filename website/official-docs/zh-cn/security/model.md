---
title: 安全模型
sidebar_label: 安全模型
---

# 安全模型

本页逐属性说明 `geelato-security` 定义的安全模型:六个核心实体、四个挂靠关系类,以及它们之间的关系规则。所有类均位于 `cn.geelato.security` 包,是纯数据契约(POJO),不携带任何来源与存储细节;平台库的字段映射见[平台默认实现](platform-defaults.md)。

## 模型总览

| 模型 | 职责 |
| --- | --- |
| `Tenant` | 租户,多租户隔离维度 |
| `App` | 应用(子系统),上下文的第四个维度 |
| `Org` | 组织节点(公司/部门/科室等),树结构 |
| `User` | 用户(登录主体),携带挂靠关系与权限 |
| `Role` | 角色,权限的载体 |
| `Permission` | 权限规则(数据权限/元素权限) |
| `UserOrg` | 用户-组织挂靠(成员身份) |
| `UserRole` | 用户直挂角色 |
| `UserOrgRole` | 用户挂组织角色(部门角色,含来源部门) |
| `OrgRole` | 组织挂靠角色(角色挂到部门) |

## 租户与应用

### Tenant

| 属性 | 类型 | 意义 |
| --- | --- | --- |
| `code` | `String` | 租户编码,租户隔离的唯一标识 |
| `name` | `String` | 租户名称 |
| `configuration` | `Map<String, Object>` | 租户级配置(当前为预留字段,内部为空 `HashMap`) |
| `properties` | `Map<String, Object>` | 租户级扩展属性(当前为预留字段,内部为空 `HashMap`) |

构造器 `Tenant(String code)` 与 `Tenant(String code, String name)`;构造时初始化两个空 Map。

### App

| 属性 | 类型 | 意义 |
| --- | --- | --- |
| `id` | `String` | 应用标识 |
| `name` | `String` | 应用名称 |
| `version` | `String` | 应用版本 |

`App` 是 `SecurityContext` 的第四个线程维度,用于子系统标识场景,见[安全上下文](context.md)。

## 组织:OrgCore 与 Org

组织模型分两层:`OrgCore` 承载树结构与租户归属,`Org` 补充公司归属与节点类型。

### OrgCore

| 属性 | 类型 | 意义 |
| --- | --- | --- |
| `orgId` | `String` | 组织节点 ID,组织引用一律用它 |
| `code` | `String` | 组织编码,业务上的稳定标识 |
| `pid` | `String` | 父节点 ID;组织树靠它逐级组织,根节点为空 |
| `name` | `String` | 组织名称 |
| `tenantCode` | `String` | 所属租户编码 |

### Org(继承 OrgCore)

| 属性 | 类型 | 意义 |
| --- | --- | --- |
| (继承) | — | `orgId`/`code`/`pid`/`name`/`tenantCode`,见上表 |
| `companyId` | `String` | 所属公司 ID(沿组织树派生,装载时由关系解析补齐) |
| `type` | `String` | 组织节点类型,取值见下表;公司归属解析的关键字段 |
| `extendId` | `String` | 公司扩展 ID,挂在公司节点上,随公司归属派生 |

`type` 的平台取值(`OrgTypeEnum`):

| 取值 | 含义 |
| --- | --- |
| `root` | 根组织 |
| `company` | 公司 |
| `department` | 部门 |
| `section` | 科室 |
| `factory` | 工厂 |
| `office` | 办事处 |

## 用户:UserCore 与 User

### UserCore

| 属性 | 类型 | 意义 |
| --- | --- | --- |
| `userId` | `String` | 用户唯一标识(平台库 `platform_user.id`),用户引用一律用它 |
| `userName` | `String` | 用户名称(显示名) |
| `loginName` | `String` | 登录名,扩展身份键之一 |
| `tenantCode` | `String` | 所属租户编码,租户隔离维度 |

### User(继承 UserCore)

`User` 是安全模型的核心,属性按用途分组如下。

**身份与档案:**

| 属性 | 类型 | 意义 |
| --- | --- | --- |
| (继承) | — | `userId`/`userName`/`loginName`/`tenantCode`,见 `UserCore` 表 |
| `jobNumber` | `String` | 工号 |
| `description` | `String` | 描述 |
| `enName` | `String` | 英文名称 |
| `sex` | `int` | 性别;`0` 女、`1` 男(`UserSexEnum`) |
| `avatar` | `String` | 头像 |
| `mobilePrefix` | `String` | 手机国际区号 |
| `mobilePhone` | `String` | 手机号 |
| `telephone` | `String` | 座机号 |
| `email` | `String` | 电子邮箱 |
| `post` | `String` | 岗位 |
| `nationCode` | `String` | 国家/地区编码 |
| `provinceCode` | `String` | 省份编码 |
| `cityCode` | `String` | 城市编码 |
| `address` | `String` | 详细地址 |

**组织归属与派生**(装载时由 `UserOrgInfoEnricher` 补齐,见[抽象装载类](loader.md)):

| 属性 | 类型 | 意义 |
| --- | --- | --- |
| `orgId` | `String` | 用户直接挂靠所在的部门或科室;为空时按挂靠关系解析(默认组织优先) |
| `orgName` | `String` | `orgId` 对应组织名称 |
| `defaultOrgId` | `String` | 默认部门 ID |
| `defaultOrgName` | `String` | 默认部门名称 |
| `cooperatingOrgId` | `String` | 合作单位 ID |
| `deptId` | `String` | 当挂靠的是科室时,该值为科室的上级部门 ID |
| `buId` | `String` | 事业部 ID,由公司归属填充(与 `companyId` 同值) |
| `buName` | `String` | 事业部名称 |
| `companyId` | `String` | 所属公司 ID(沿组织树解析出的公司节点) |
| `companyName` | `String` | 公司名称 |
| `extendId` | `String` | 公司扩展 ID,取自公司节点的 `extendId` |

**状态:**

| 属性 | 类型 | 意义 |
| --- | --- | --- |
| `type` | `int` | 账号类型,取值见下表 |
| `source` | `int` | 账号来源,取值见下表 |
| `enableStatus` | `int` | 启用状态:`0` 禁用、`1` 启用 |

`type` 的平台取值(`UserTypeEnum`):

| 取值 | 含义 |
| --- | --- |
| `0` | 员工账号 |
| `1` | 系统账号 |
| `2` | 企业外人员 |
| `3` | 虚拟员工账号 |
| `999` | 租户管理员 |

`source` 的平台取值(`UserSourceEnum`):

| 取值 | 含义 |
| --- | --- |
| `0` | 本地用户 |
| `1` | 系统同步 |

**扩展身份键:**

| 属性 | 类型 | 意义 |
| --- | --- | --- |
| `weixinUnionId` | `String` | 微信用户统一标识,扩展身份键 |
| `weixinWorkUserId` | `String` | 企业微信用户 ID,扩展身份键 |

`loginName`、`weixinUnionId`、`weixinWorkUserId` 组成三种扩展身份入口,供扩展键认证使用(见[认证鉴权](../authentication/security-authentication.md)),`UserProvider.getUserByExtendKey` 按键类型查询。

**委托代办:**

| 属性 | 类型 | 意义 |
| --- | --- | --- |
| `delegateUserId` | `String` | 被委托人(实际执行者)ID;为空表示本人操作 |
| `delegateUserName` | `String` | 被委托人名称 |

语义:本次操作由代理人代为执行时,`creator`/`updater` 仍记录当前生效身份(委托人),`delegateUserId`/`delegateUserName` 仅供审计区分"是谁代替谁操作的"。引擎(系统主体)代替用户落库时同语义适用:生效身份=归属用户,delegate 字段=引擎系统身份。

**通用扩展:**

| 属性 | 类型 | 意义 |
| --- | --- | --- |
| `extendInfo` | `Map<String, Object>` | 通用扩展属性,由上层通过 `UserExtInfoEnricher` 在快照装载时填充;具体业务(如 git 绑定)以约定键存放,`User` 层不感知键含义,值应为不可变对象(字符串为主) |

:::note 两个相近命名的区分
`extendId` 是公司扩展 ID(`String`,取自公司节点);`extendInfo` 是通用扩展属性(`Map`)。两者是不同的概念。
:::

**主体标志:**

| 属性 | 类型 | 意义 |
| --- | --- | --- |
| `systemPrincipal` | `boolean` | 是否系统后台主体(定时任务等无 HTTP 上下文的场景)。为 `true` 时数据权限过滤跳过默认的 `creator='<userId>'` 条件,能查询当前租户下全部数据;租户隔离(`tenantCode` 过滤)仍然生效 |

**关系与权限集合:**

| 属性 | 类型 | 意义 |
| --- | --- | --- |
| `userOrgs` | `List<UserOrg>` | 组织挂靠(成员身份),多组织挂靠一并返回 |
| `userRoles` | `List<UserRole>` | 直挂角色 |
| `userOrgRoles` | `List<UserOrgRole>` | 组织挂靠角色(含来源部门) |
| `defaultOrg` | `UserOrg` | 默认组织对象(挂靠关系中 `defaultOrg=true` 的一条) |
| `tenant` | `Tenant` | 所属租户对象 |
| `dataPermissions` | `List<Permission>` | 数据权限规则集合 |
| `elementPermissions` | `List<Permission>` | 元素权限集合 |

:::note 权限集合的默认装载
默认平台快照不填充权限集合——权限域数据在认证中心侧,由 `SecurityProvider.queryPermissionsOfUser` 按需查询(见[Provider 契约](provider.md))。快照式全量装载权限属于认证中心实现(`AuthCenterSecurityProvider`)的场景。
:::

### User 的行为方法

| 方法 | 语义 |
| --- | --- |
| `getEffectiveRoles()` | 生效角色 = 直挂角色(`userRoles`)∪ 组织挂靠角色(`userOrgRoles`),按角色 `code` 去重。契约生效语义的统一入口;历史消费方(如 `SecurityContext.isAdmin`)继续只看直挂角色,行为不变 |
| `getDataPermissionByEntity(String entity)` | 按实体取数据权限:过滤 `dataPermissions` 中 `entity` 匹配的规则,取 `weight` 最大者(同权重取首条);无集合或无匹配返回 `null` |
| `setupOrgInfo(OrgProvider orgProvider)` | 历史兼容入口:经 `OrgProvider` 填充 `orgName`/`companyId`/`companyName`/`extendId`/`buId`/`buName`/`deptId`(空时)/`defaultOrgName`。新代码请优先使用 `UserOrgInfoEnricher` 补齐组织派生信息 |

## 角色:Role

| 属性 | 类型 | 意义 |
| --- | --- | --- |
| `Id` | `String` | 角色 ID。注意字段名首字母大写,getter/setter 为 `getId`/`setId` |
| `code` | `String` | 角色编码;业务代码与数据权限规则引用角色一律用 `code`(跨环境稳定),不用 ID |
| `name` | `String` | 角色名称 |
| `type` | `String` | 角色类型,取值见下表 |
| `tenantCode` | `String` | 所属租户编码 |

`type` 的平台取值(`RoleTypeEnum`):

| 取值 | 含义 |
| --- | --- |
| `platform` | 平台级角色 |
| `app` | 应用级角色 |

## 权限:Permission

| 属性 | 类型 | 意义 |
| --- | --- | --- |
| `code` | `String` | 权限编码,权限的引用标识(`hasPermission` 按 `code` 判断) |
| `entity` | `String` | 权限所属实体名;数据权限按实体生效 |
| `name` | `String` | 权限名称 |
| `rule` | `String` | 权限规则表达式。数据权限为过滤条件,可含 `#currentUser.xxx#` 用户变量;元素权限为元素标识 |
| `weight` | `Integer` | 数据权限权重;同一实体多条规则取 `weight` 最大者生效 |
| `roleWeight` | `Integer` | 来源角色的权重(权限经角色继承时携带) |

### rule 中的用户变量

`PermissionRuleUtils.replaceRuleVariable(Permission, User)` 在查询前把 `rule` 中的用户变量替换为当前用户字段值。语法:`#currentUser.字段名#`,支持 `#currentUser.defaultOrg.字段名#` 访问默认组织对象的字段。

允许的字段白名单(严格驼峰命名,白名单之外的字段抛 `IllegalArgumentException`):

| 类别 | 允许的字段 |
| --- | --- |
| 用户基本字段 | `userId`、`orgId`、`defaultOrgId`、`cooperatingOrgId`、`companyId`、`extendId`、`deptId`、`buId`、`weixinUnionId`、`weixinWorkUserId`、`tenantCode` |
| `defaultOrg` 对象字段 | `defaultOrg.orgId`、`defaultOrg.deptId`、`defaultOrg.companyId`、`defaultOrg.extendId` |

替换行为:

- 字段值非空时替换为带引号的字符串字面量(如 `#currentUser.userId#` → `'123456'`),为空时替换为 `null`。
- 多条规则合并(`replaceRuleVariable(List<Permission>, User)`)时,去重、压缩空白后以 ` OR ` 拼接,每条包一层括号。
- 替换完成后残留任何 `#...#` 占位符即抛异常(携带实体名、原始规则与占位符清单),不允许带未解析变量进入查询。

## 挂靠关系类

### UserOrg(用户-组织挂靠,继承 OrgCore)

用户与组织的成员身份关系,一个用户可挂靠多个组织。

| 属性 | 类型 | 意义 |
| --- | --- | --- |
| (继承) | — | `orgId`(挂靠组织 ID)/`code`/`pid`/`name`/`tenantCode` |
| `fullName` | `String` | 组织全名(全路径名,空时由 enricher 以 `name` 补齐) |
| `deptId` | `String` | 该组织的上级部门 ID(派生) |
| `companyId` | `String` | 所属公司 ID(派生) |
| `extendId` | `String` | 所属公司的扩展 ID(派生) |
| `defaultOrg` | `Boolean` | 是否默认组织;`true` 时该挂靠即用户的默认部门 |
| `type` | `String` | 组织类型,取值同 `Org.type` |
| `category` | `String` | 组织类别,取值见下表 |
| `description` | `String` | 描述 |

`category` 的平台取值(`OrgCategoryEnum`):

| 取值 | 含义 |
| --- | --- |
| `inside` | 内部 |
| `outside` | 外部 |
| `virtual` | 虚拟 |

### UserRole(用户直挂角色,继承 Role)

用户直挂角色,无新增字段。角色直接授予用户,不经组织。

### UserOrgRole(用户挂组织角色,继承 Role)

用户挂组织角色契约模型:userId × orgId × roleId 三元关系(嵌于 `User` 快照时 userId 隐含)。

| 属性 | 类型 | 意义 |
| --- | --- | --- |
| (继承) | — | 角色字段,见 `Role` 表 |
| `orgId` | `String` | 角色来源部门(角色挂靠的组织)ID |

语义:部门角色对用户生效的**唯一途径**——用户经本关系直挂。用户可以不是来源部门的成员(成员身份见 `UserOrg`,与本关系独立,不产生角色继承)。

### OrgRole(组织挂靠角色,继承 Role)

组织挂靠角色(角色挂组织)契约模型。

| 属性 | 类型 | 意义 |
| --- | --- | --- |
| (继承) | — | 角色字段,见 `Role` 表 |
| `orgId` | `String` | 角色挂靠的组织(部门)ID |

语义:角色挂靠到某个组织(部门)上,由该部门提供。**仅直属部门生效,不沿组织树向上递归**。组织挂靠本身不使部门成员自动获得该角色——部门角色对用户生效的唯一途径是用户经 `UserOrgRole` 直挂。

### UserRoles(两类挂靠结果的聚合)

| 属性 | 类型 | 意义 |
| --- | --- | --- |
| `userRoles` | `List<UserRole>` | 用户直挂角色 |
| `userOrgRoles` | `List<UserOrgRole>` | 用户挂组织角色(部门角色,`orgId` 为角色来源部门) |

`getEffectiveRoles()` 与 `User.getEffectiveRoles()` 同一语义:直挂 ∪ 组织挂靠,按角色 `code` 去重。

## 关系规则

1. **组织树**:`Org` 靠 `pid` 逐级组织,根节点 `pid` 为空;`type` 标识节点类型。关系解析(部门/公司归属)沿父链上溯,环路以 visited 集合防护。
2. **用户-组织**:多对多挂靠(`UserOrg`),`defaultOrg=true` 标识默认组织;`User.orgId` 为空时按挂靠关系解析(默认组织优先,其次第一条挂靠)。
3. **角色授予两形态**:直挂(`UserRole`)与部门角色(`UserOrgRole`,userId × orgId × roleId,用户可不为此部门成员)。两者都是用户侧关系。
4. **角色-组织挂靠**(`OrgRole`):角色挂到部门,仅直属部门生效,不递归;挂靠本身不给成员自动授权,对用户生效必须经 `UserOrgRole` 直挂。
5. **生效角色**:直挂 ∪ 组织挂靠,按 `code` 去重,统一从 `getEffectiveRoles()` 取。
6. **公司归属**:沿父链取第一个 `type=company` 的祖先,无则回退最顶层祖先;`companyId`/`companyName`/`extendId`/`buId`/`buName` 随之派生。
7. **数据权限生效**:按 `entity` 分组,`weight` 最大者生效(`getDataPermissionByEntity`);多条规则经变量替换后以 ` OR ` 合并。
8. **系统主体**:`systemPrincipal=true` 时跳过数据权限过滤但保留租户隔离,用于定时任务等后台场景。
9. **委托代办**:生效身份=委托人(落 `creator`/`updater`),`delegateUserId`/`delegateUserName` 记录实际执行者,仅供审计。

## 总结

- 模型层是纯数据契约:实体(租户/应用/组织/用户/角色/权限)+ 关系类(组织挂靠/三种角色挂靠),不含任何来源细节。
- 用户是模型的中心:组织归属与公司归属字段均为派生值,装载时由 enricher 补齐;权限集合默认不装载,属认证中心域。
- 角色生效语义收敛在 `getEffectiveRoles()`:直挂 ∪ 组织挂靠按 code 去重;部门角色对用户生效的唯一途径是 `UserOrgRole` 直挂。
- 数据权限以 `entity` + `weight` 决定生效规则,规则变量仅支持白名单字段,未解析占位符硬失败。

## 推荐继续阅读

- [Provider 契约](provider.md)——这些模型如何被读取
- [模型快照与快照加载](snapshot.md)——模型如何被装载成快照
- [安全上下文](context.md)——模型如何进入线程上下文
