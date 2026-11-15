---
title: 模型快照与快照加载
sidebar_label: 模型快照
---

# 模型快照与快照加载

本页说明三个快照类(`OrgSnapshot`/`UserSnapshot`/`RoleSnapshot`)的索引结构、不可变性保证,以及快照的统一装载与刷新链路。快照由[装载 SPI](loader.md) 产出、经 Provider 缓存对外,是"读侧无锁"的基础。

## 快照总览

| 快照 | 索引结构 | 产生自 |
| --- | --- | --- |
| `OrgSnapshot` | `orgById` | `OrgSnapshotLoader.load()` |
| `UserSnapshot` | `userById` + `userByExtendType` 二级索引 | `UserSnapshotLoader.load(enricher)` |
| `RoleSnapshot` | `roleById` + `roleByCode` + `orgRolesByOrgId` | `RoleSnapshotLoader.load()` |

三个快照均为 `final` 类、构造器私有,只经静态 `from(...)` 工厂方法创建;查询方法为包级可见——快照不直接对外,统一经 Provider 暴露。

## OrgSnapshot

| 成员 | 意义 |
| --- | --- |
| `orgById` | 组织 ID → `Org` 索引 |
| `from(Map<String, Org>)` | 静态工厂:拷贝为 `LinkedHashMap`(保留装载顺序)后以 `Collections.unmodifiableMap` 包装 |
| `EMPTY` | 空快照单例,Provider 初始态 |
| `getOrg(String orgId)` | 包级查询,不存在返回 `null` |

## UserSnapshot

| 成员 | 意义 |
| --- | --- |
| `userById` | 用户 ID → `User` 索引 |
| `userByExtendType` | 扩展身份二级索引:`type → extendKey → User`;`type` 即 `loginName`/`weixinUnionId`/`weixinWorkUserId` 三类扩展键 |
| `from(Map, Map)` | 静态工厂:外层与每层内层 Map 都做不可变包装(深度不可变) |
| `EMPTY` | 空快照单例 |
| `getUser(String userId)` | 包级查询 |
| `getUserByExtendKey(String type, String extendKey)` | 包级查询:先取 `type` 对应的池,再按键取用户;池不存在返回 `null` |

二级索引是扩展键认证(微信登录、企业微信登录)的直接数据支撑:`UserProvider.getUserByExtendKey` 归一化 `type` 后落到这张索引。

## RoleSnapshot

| 成员 | 意义 |
| --- | --- |
| `roleById` | 角色 ID → `Role` 索引 |
| `roleByCode` | 角色 code → `Role` 索引,由 `from()` 从 `roleById` 构建;重复 code `putIfAbsent` 首见胜出,不覆盖 |
| `orgRolesByOrgId` | 组织 ID → `List<OrgRole>` 索引(部门挂靠的角色);每个列表经 `List.copyOf` 不可变化 |
| `EMPTY` | 空快照单例 |
| `getRole(String roleId)` / `getRoleByCode(String roleCode)` | 包级查询 |
| `getOrgRoles(String orgId)` | 包级查询,无记录返回空列表 |

## 不可变性与并发模型

- **final + 私有构造 + 不可变包装**:快照一旦发布,内容不可修改;`from()` 在发布前完成拷贝与包装。
- **整体装载、原子替换**:Provider 内以 `AtomicReference` 持有快照引用,`refresh()` 装载新快照后一次性替换引用。读侧无锁,任何时刻看到的都是完整快照,不存在"半新半旧"。
- **每次查询取当前引用**:Provider 每个方法先取 `snapshotRef.get()` 再查,天然与 refresh 语义一致;`BridgeSecurityProvider` 不自行缓存,同理。

## 刷新链路:SecurityDataRefreshCoordinator

组织/用户/角色三快照的统一装载与级联刷新协调器,位于框架层默认实现(`geelato-core`)。

| 方法 | 语义 |
| --- | --- |
| `init()`(`@PostConstruct`) | 启动时执行 `refreshAll()`,完成首次装载 |
| `refreshAll()` | `orgProvider.refresh()` → `userProvider.refresh()` → 角色快照刷新 |
| `refreshOrg()` | 刷新组织快照并**级联刷新用户快照**(组织变化影响用户的派生字段:公司/部门/BU) |
| `refreshUser()` | 仅刷新用户快照 |
| `refreshRole()` | 仅刷新角色快照 |

要点:

- 全部刷新方法 `synchronized`,避免并发刷新交叉。
- `UserProvider`/`RoleProvider` 可缺席(`@Autowired(required = false)`,直连式实现自行管理数据面时),缺席则跳过对应刷新。
- 运行期触发点:组织或用户目录变更后,由平台控制器调用——`OrgController.refreshOrg()` 与 `UserController.refreshUser()`(见[平台默认实现](platform-defaults.md)的装配表)。

## 总结

- 三个快照是不可变索引容器:组织单索引,用户双索引(含扩展身份二级索引),角色三索引(ID/code/部门挂靠)。
- 不可变性 + 原子替换使读侧无锁:装载线程构建完毕才发布,任何读请求看到的都是完整一致的快照。
- `SecurityDataRefreshCoordinator` 统一刷新:启动全量、组织变更级联用户、用户/角色可单独刷新。

## 推荐继续阅读

- [抽象装载类](loader.md)——快照从哪里来
- [平台默认实现](platform-defaults.md)——默认 JDBC 装载与装配
- [Provider 契约](provider.md)——快照之上的查询语义
