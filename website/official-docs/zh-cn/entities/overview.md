---
title: 平台实体总览
sidebar_label: 概览
---

# 平台实体总览

`geelato-meta` 是平台业务实体的集中定义模块：平台自身运行所需的实体——应用、组织、用户、角色权限、字典、消息通知、文件附件、调度集成等——全部在该模块的 `cn.geelato.meta` 包下用 `@Entity` 注解声明，共 54 个。本章按业务域逐实体给出字段级目录；其他特性模块（业务告警、业务异常、邮件等）的实体见[全平台实体索引](platform-entity-index.md)。

## 模块定位

| 项 | 说明 |
| --- | --- |
| 模块坐标 | `geelato-meta`（`cn.geelato.meta` 包） |
| 上游依赖 | `geelato-utils`、`geelato-lang`（注解）、`geelato-core`（实体基类与元数据模型） |
| 下游消费方 | `geelato-web-common`、`geelato-web-platform`、`geelato-mcp-fms`、`geelato-mcp-platform` |
| 注册方式 | 零手工注册：启动时 `MetaConfiguration` 按 `geelato.meta.scan-package-names`（默认 `cn.geelato`）扫描 `@Entity` 注解类，自动注册进 `MetaManager` |

实体元数据如何被解析、缓存与消费（ORM SQL 生成、MQL 校验、数据源路由等）属于框架元数据机制，见[元数据说明](../metadata/overview.md)；`@Entity`/`@Col`/`@Title` 等注解的写法见[ORM 注解说明](../orm/annotations.md)。

## 公共基类字段

实体继承链为 `IdEntity → BaseEntity → BaseSortableEntity`，附件域另有公共基类 `Attachment`（本身不注册实体）。以下公共字段不再在各实体条目的字段表中重复列出。

### IdEntity

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| id | id | String | 主键，`@Id`，标题"序号" |

### BaseEntity

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| createAt | create_at | Date | 创建时间，非空 |
| creator | creator | String | 创建者，非空 |
| creatorName | creator_name | String | 创建者名称 |
| buId | bu_id | String | 单位。bu 即 business unit，记录（分）公司编码，用于分公司、事业部的数据权限区分 |
| deptId | dept_id | String | 部门 |
| tenantCode | tenant_code | String | 租户编码 |
| updateAt | update_at | Date | 更新时间，非空 |
| updater | updater | String | 更新者，非空 |
| updaterName | updater_name | String | 更新者名称 |
| deleteAt | delete_at | Date | 删除时间 |
| delStatus | del_status | int | 逻辑删除状态：1 已删除、0 未删除。默认 0 |

### BaseSortableEntity

在 `BaseEntity` 之上增加：

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| seqNo | seq_no | long | 次序，排序用途，默认 0 |

### Attachment（附件域基类）

`Attach`、`Compress`、`Resources` 三个实体全部字段继承自该基类，自身无新增字段：

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| pid | pid | String | 父 id，附件层级 |
| appId | app_id | String | 所属应用 |
| name | name | String | 名称 |
| type | type | String | 类型（MIME） |
| genre | genre | String | 分类，逗号分隔，可多值 |
| size | size | Long | 大小（字节） |
| path | path | String | 绝对地址 |
| objectId | object_id | String | 对象 id |
| formIds | form_ids | String | 表单 id |
| invalidTime | invalid_time | Date | 失效时间 |
| batchNo | batch_no | String | 批次号 |
| resolution | resolution | String | 分辨率，像素（px） |
| source | — | String | `@Transient` 非落库 |
| storageType | — | String | `@Transient` 非落库，存储类型 |

## 启用状态（enable_status）

`enable_status` 不在公共基类中。实现 `EntityEnableAble` 接口的实体共 9 个：Role、User、Dict、DictItem、SysConfig、Qrcode、ExportTemplate、UserEmailAccount、UserDelegateMap，取值 1 启用、0 停用（默认 1）。`Api`、`AppSqlMap`、`EncodingLog` 有同义字段但未实现该接口。

## 数据落库分布（catalog）

`@Entity(catalog)` 决定实体归属的库：

| catalog | 实体 | 说明 |
| --- | --- | --- |
| `platform` | 48 个 | 平台主库，绝大多数实体 |
| `email` | EncodingLog | 编码记录，落邮件库 |
| `platform-log` | PlatformExceptionLog | 平台错误日志，独立日志库 |
| 未标注 | Area、Compress、Message、Notice、Qrcode、UserStockMap | 按默认数据源归属规则解析，见[实体绑定与优先级](../dynamic-datasource/entity-binding.md) |

## 业务域分组与章节导航

54 个实体按业务域分为 8 页，各页实体条目均含：实体表名、继承说明、自有字段全表（公共字段见上，不重复）。

| 页面 | 业务域 | 成员 |
| --- | --- | --- |
| [应用与设计器](app-and-designer.md) | 应用定义、页面协作、多语言、菜单导航 | App、AppVersion、AppPage、AppPageCheckReq、AppMultiLang、AppPageLang、TreeNode、MenuItem |
| [接口与编排授权](api-orchestration.md) | 接口服务、自定义编排、导出模板、应用授权关系 | Api、ApiParam、CustomSql、AppSqlMap、AppTableMap、AppViewMap、ExportTemplate |
| [组织与用户](org-tenant-user.md) | 组织、公司、用户、行政区划、租户 | Org、Company、User、Area、Tenant、TenantSite |
| [角色与权限](role-permission.md) | 角色、权限点与全部权限类关联表 | Role、Permission、RoleUserMap、OrgRoleMap、OrgUserMap、UserOrgRoleMap、RolePermissionMap、UserPermissionMap、RoleAppMap、RoleTreeNodeMap、UserDelegateMap |
| [消息与通知](message-notification.md) | 站内消息、通知公告、通知流水线、邮箱账号 | Message、Notice、Notification、NotificationUser、NotificationOutbox、UserEmailAccount、UserStockMap |
| [字典配置与文件](dict-config-file.md) | 数据字典、系统配置、二维码、文件附件 | Dict、DictItem、SysConfig、Qrcode、FileInfo、Attach、Compress、Resources |
| [调度集成与日志](schedule-integration-log.md) | 调度任务、ORM 钩子、各类日志 | Schedule、OrmHook、AppPageLog、ScheduleLog、OrmHookLog、EncodingLog、PlatformExceptionLog |
| [全平台实体索引](platform-entity-index.md) | 跨模块实体全景 | geelato-meta 全部 54 个 + 告警/异常/邮件/平台扩展等模块实体索引 |

## 约定与阅读提示

- 字段表"列名"默认取 `@Col(name)`；未标注 `@Col` 的字段按属性名驼峰转蛇形推导列名。
- 字段含义以源码字段级 `@Title`（title + description）为准。个别实体的类级标题存在注释失真（如 Schedule 类级标题误写为"应用"、Tenant 误写为"租户站点"、UserStockMap 名含 stock 实为常用联系人），本章按实际语义撰写并就地加注。
- `*Map` 后缀实体为关联表（两张表之间的映射），通常字段很少。
