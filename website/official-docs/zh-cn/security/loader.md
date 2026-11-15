---
title: 抽象装载类
sidebar_label: 装载类
---

# 抽象装载类

本页说明 `geelato-security` 的三个快照装载 SPI 与两个装载辅助契约。装载类是"外部安全数据 → 不可变快照"的装配边界:框架与平台不直接读库,而是经这三个接口把数据装配成[快照](snapshot.md),由 Provider 缓存对外(见[Provider 契约](provider.md))。

## 三个装载 SPI

| 接口 | 方法 | 语义 |
| --- | --- | --- |
| `OrgSnapshotLoader` | `OrgSnapshot load()` | 装载组织快照 |
| `UserSnapshotLoader` | `UserSnapshot load(UserOrgInfoEnricher userOrgInfoEnricher)` | 装载用户快照 |
| `RoleSnapshotLoader` | `RoleSnapshot load()` | 装载角色快照 |

三个接口均只有一个 `load()` 方法,契约要点:

- **职责单一**:把外部来源的数据一次性装配成完整快照,每次调用返回全新快照,不做增量。
- **允许上层替换**:接口 javadoc 明确"允许上层替换默认平台表读取实现"——默认实现读平台主库(JDBC),宿主工程可整体换成外部服务、配置中心、Redis、聚合接口等任意来源。
- **`UserSnapshotLoader` 为何携带 enricher 参数**:用户的组织派生信息(部门/公司/扩展 ID)依赖组织关系解析,装载器把派生补齐委托给调用方注入的 `UserOrgInfoEnricher`,避免装载器反向依赖 `OrgProvider`。装载器在装配过程中逐个调用 `enrich(UserOrg)` 与 `enrich(User)`。

## 装载辅助契约

两个 Enricher 是装载过程的补齐器,填充模型中的派生字段与扩展字段。

### UserOrgInfoEnricher(用户组织派生信息补齐器)

| 方法 | 语义 |
| --- | --- |
| `enrich(User user)` | 为用户补齐组织派生字段:`orgName`/`companyId`/`companyName`/`extendId`/`buId`/`buName`/`deptId`/`defaultOrgName` |
| `enrich(UserOrg userOrg)` | 为单条组织挂靠补齐派生字段:`name`/`deptId`/`companyId`/`extendId`/`fullName` |

数据来自组织关系解析(依赖组织数据),因此默认实现构造时注入 `OrgProvider`。

### UserExtInfoEnricher(用户扩展属性补齐器)

| 方法 | 语义 |
| --- | --- |
| `enrich(User user)` | 在用户快照装载时填充 `User.extendInfo` |

数据来源(绑定表/配置/认证中心)由上层决定;键约定由消费方(如 git 操作模块)定义,`User` 层不感知键含义。缺省实现为 no-op(用户未绑定扩展信息),见[平台默认实现](platform-defaults.md)。

## 装载与刷新的契约语义

- `load()` 每次返回全新构建的不可变快照;装载线程构建完毕后才发布,读侧不感知装载过程。
- Provider 的 `refresh()` 调用 `load()` 后整体原子替换快照引用,刷新链路见[模型快照与快照加载](snapshot.md)。
- 替换 Loader 实现时保持 `refresh()` 语义稳定:能够重新加载最新快照,不要把 `refresh()` 实现成有副作用的重事务流程。

## 总结

- 装载 SPI 只有三个 `load()` 方法:组织、用户、角色各一;职责是把外部来源装配成不可变快照,允许上层整体替换默认 JDBC 实现。
- 两个 Enricher 承担派生补齐:`UserOrgInfoEnricher` 填组织派生字段(经 `OrgProvider` 解析),`UserExtInfoEnricher` 填 `extendInfo`(来源由上层决定)。
- `UserSnapshotLoader.load` 携带 enricher 参数是刻意的依赖方向设计:装载器不依赖 Provider。

## 推荐继续阅读

- [模型快照与快照加载](snapshot.md)——`load()` 产出的快照长什么样
- [平台默认实现](platform-defaults.md)——默认 JDBC 装载与替换方式
- [Provider 契约](provider.md)——装载结果如何被读取
