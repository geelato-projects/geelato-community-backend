---
title: Platform Entities Overview
sidebar_label: Overview
---

# Platform Entities Overview

`geelato-meta` is the module where the platform's business entities are centrally defined: the entities the platform itself needs to run — applications, organizations, users, roles and permissions, dictionaries, message notifications, file attachments, scheduling and integration — are all declared with the `@Entity` annotation in the module's `cn.geelato.meta` package, 54 in total. This chapter provides a field-level catalog, entity by entity, grouped by business domain; entities of other feature modules (business alerting, business exceptions, mail, etc.) are listed in the [Platform Entity Index](platform-entity-index.md).

## Module Position

| Item | Description |
| --- | --- |
| Module coordinates | `geelato-meta` (`cn.geelato.meta` package) |
| Upstream dependencies | `geelato-utils`, `geelato-lang` (annotations), `geelato-core` (entity base classes and meta model) |
| Downstream consumers | `geelato-web-common`, `geelato-web-platform`, `geelato-mcp-fms`, `geelato-mcp-platform` |
| Registration | Zero manual registration: at startup `MetaConfiguration` scans `@Entity` classes under `geelato.meta.scan-package-names` (default `cn.geelato`) and registers them into `MetaManager` automatically |

How entity metadata is parsed, cached and consumed (ORM SQL generation, MQL validation, datasource routing, etc.) belongs to the framework metadata mechanism; see [Metadata Overview](../metadata/overview.md). For how to write entities with `@Entity`/`@Col`/`@Title` annotations, see [ORM Annotations](../orm/annotations.md).

## Common Base-Class Fields

The entity inheritance chain is `IdEntity → BaseEntity → BaseSortableEntity`, with an extra common base class `Attachment` for the attachment domain (the base class itself is not a registered entity). The common fields below are not repeated in each entity's field table.

### IdEntity

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| id | id | String | Primary key, `@Id`, titled "序号" (serial number) |

### BaseEntity

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| createAt | create_at | Date | Creation time, non-null |
| creator | creator | String | Creator, non-null |
| creatorName | creator_name | String | Creator name |
| buId | bu_id | String | Business unit. bu means business unit: the code of a (branch) company, used to distinguish data permissions across branch companies or business units |
| deptId | dept_id | String | Department |
| tenantCode | tenant_code | String | Tenant code |
| updateAt | update_at | Date | Update time, non-null |
| updater | updater | String | Updater, non-null |
| updaterName | updater_name | String | Updater name |
| deleteAt | delete_at | Date | Delete time |
| delStatus | del_status | int | Logical delete status: 1 deleted, 0 not deleted. Defaults to 0 |

### BaseSortableEntity

Adds the following on top of `BaseEntity`:

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| seqNo | seq_no | long | Sequence number for ordering, defaults to 0 |

### Attachment (attachment domain base class)

The three entities `Attach`, `Compress` and `Resources` inherit all their fields from this base class:

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| pid | pid | String | Parent id, attachment hierarchy |
| appId | app_id | String | Owning application |
| name | name | String | Name |
| type | type | String | Type (MIME) |
| genre | genre | String | Genre, comma-separated, multi-valued |
| size | size | Long | Size (bytes) |
| path | path | String | Absolute path |
| objectId | object_id | String | Object id |
| formIds | form_ids | String | Form ids |
| invalidTime | invalid_time | Date | Expiration time |
| batchNo | batch_no | String | Batch number |
| resolution | resolution | String | Resolution, in pixels (px) |
| source | — | String | `@Transient`, not persisted |
| storageType | — | String | `@Transient`, not persisted; storage type |

## Enable Status (enable_status)

`enable_status` is not part of the common base class. 9 entities implement the `EntityEnableAble` interface: Role, User, Dict, DictItem, SysConfig, Qrcode, ExportTemplate, UserEmailAccount, UserDelegateMap; values are 1 enabled, 0 disabled (default 1). `Api`, `AppSqlMap` and `EncodingLog` carry a same-named field without implementing the interface.

## Storage Distribution (catalog)

`@Entity(catalog)` decides which database an entity belongs to:

| catalog | Entities | Description |
| --- | --- | --- |
| `platform` | 48 | The platform primary database; the vast majority of entities |
| `email` | EncodingLog | Encoding records, stored in the mail database |
| `platform-log` | PlatformExceptionLog | Platform error log, separate log database |
| Unspecified | Area, Compress, Message, Notice, Qrcode, UserStockMap | Resolved by the default datasource attribution rules; see [Entity Binding & Priority](../dynamic-datasource/entity-binding.md) |

## Domain Grouping and Chapter Navigation

The 54 entities are split into 8 pages by business domain. Each entity entry contains: table name, inheritance notes, and a full table of its own fields (common fields are listed above and not repeated).

| Page | Domain | Members |
| --- | --- | --- |
| [Applications & Designer](app-and-designer.md) | Application definition, page collaboration, multi-language, menus & navigation | App, AppVersion, AppPage, AppPageCheckReq, AppMultiLang, AppPageLang, TreeNode, MenuItem |
| [API & Orchestration Authorization](api-orchestration.md) | API services, custom orchestration, export templates, application authorization relations | Api, ApiParam, CustomSql, AppSqlMap, AppTableMap, AppViewMap, ExportTemplate |
| [Organization & Users](org-tenant-user.md) | Organizations, companies, users, administrative divisions, tenants | Org, Company, User, Area, Tenant, TenantSite |
| [Roles & Permissions](role-permission.md) | Roles, permission points and all permission-related relation tables | Role, Permission, RoleUserMap, OrgRoleMap, OrgUserMap, UserOrgRoleMap, RolePermissionMap, UserPermissionMap, RoleAppMap, RoleTreeNodeMap, UserDelegateMap |
| [Messages & Notifications](message-notification.md) | In-app messages, notices, notification pipeline, mailbox accounts | Message, Notice, Notification, NotificationUser, NotificationOutbox, UserEmailAccount, UserStockMap |
| [Dictionary, Config & Files](dict-config-file.md) | Dictionaries, system config, QR codes, files & attachments | Dict, DictItem, SysConfig, Qrcode, FileInfo, Attach, Compress, Resources |
| [Scheduling, Integration & Logs](schedule-integration-log.md) | Scheduled tasks, ORM hooks, various logs | Schedule, OrmHook, AppPageLog, ScheduleLog, OrmHookLog, EncodingLog, PlatformExceptionLog |
| [Platform Entity Index](platform-entity-index.md) | Cross-module entity panorama | All 54 geelato-meta entities + entity index of alert/exception/mail/platform-capability modules |

## Conventions and Reading Notes

- The "Column" in each field table comes from `@Col(name)`; fields without `@Col` derive their column name from the property name via camelCase-to-snake_case conversion.
- Field meanings follow the field-level `@Title` (title + description) in the source code. A few class-level titles are distorted (e.g. Schedule's class title mislabeled as "应用" while it is actually a scheduled task; Tenant mislabeled as "租户站点"; UserStockMap contains "stock" in its name but actually means frequent contacts). This chapter documents actual semantics and notes the distortion in place.
- Entities suffixed with `*Map` are relation tables (mappings between two tables) and usually carry few fields.
