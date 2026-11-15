---
id: contents
title: Packaged Contents & Runtime Roles
sidebar_label: Packaged Contents
---

# Packaged Contents & Runtime Roles

This page describes what an application package carries and the role each kind of content plays at runtime. The packing scope is determined jointly by the platform-table whitelist and each entity's data-pack strategy: the 19 platform tables are packed in full per app, and business tables are carried only when configured so at modeling time.

## Scope Mechanism

Packing runs `select *` per table with the following row scope:

| Table | Scope | Description |
| --- | --- | --- |
| `platform_app` | by `id` | The single app row; written into the package header `appCode/appName`, not into `appMetaList` |
| `platform_tree_node` | by `tree_id` | The menu tree rooted at the app id |
| Other platform tables (17) | by `app_id` | Whitelist detailed per layer below |
| Business data tables | by `app_id` | Only entity tables with `pack_bus_data` > 0 and enabled |

Packable platform tables are restricted by the whitelist `PlatformTableConstant`; an application package can never carry tables outside it (tenants, users, platform-level configuration, etc.).

## App Body

| Table | Entity | Carried contents |
| --- | --- | --- |
| `platform_app` | `App` | Name/code/icon/theme/home link/watermark, designer file tree `tree` (jsTree JSON), dependency declaration `depend_app_code`, app secret `app_key` and `token`, version info `version_info` |

Runtime role: the basis of app registration and entry — the user-visible app list is computed from the role-app relation (`platform_role_r_app`); `depend_app_code` declares dependent app module codes (e.g. `dev,sys`) for the front end to load at startup; the header `basePlatformVersion` comes from the platform app `geelato_admin`'s `version_info` and is quoted in the deployment validation upgrade message.

## UI Layer

| Table | Entity | Carried contents | Runtime role |
| --- | --- | --- | --- |
| `platform_app_page` | `AppPage` | Page source `source_content` and released `release_content` (component tree JSON), checkout status | The runtime front end renders pages by fetching `release_content` via `GET /api/page/getPageAndCustom/{pageId}`; event logic embedded in pages travels inside the page JSON |
| `platform_tree_node` | `TreeNode` | Menu tree (`tree_id` = app id), leaf `extend_id` pointing to pages | The front end builds the navigation menu from the tree; clicking a menu leaf renders the page located by `extend_id` |

## Data Layer

| Table | Entity | Carried contents | Runtime role |
| --- | --- | --- | --- |
| `platform_dev_db_connect` | `ConnectMeta` | Database connection definitions (dbType/host/db name/credentials…) | Registered as dynamic data sources after startup; an entity's `connect_id` routes it to its database |
| `platform_dev_table` | `TableMeta` | Entity definitions: entity name/table name/datasource/`pack_bus_data` strategy/cross-app flag | Together with the three tables below, parsed at startup by `MetaManager` into in-memory `EntityMeta`, driving ORM reads/writes, the generic GQL endpoints (`/api/meta/list|save|delete|tree`) and MQL commands with no per-table Java code |
| `platform_dev_column` | `ColumnMeta` | Field definitions: column/type/label/validation | Same as above; API output shapes and page form fields derive from field metadata |
| `platform_dev_table_foreign` | `TableForeign` | Foreign keys / constraints | Basis of relation validation and cascades |
| `platform_dev_view` | `TableView` | View definitions (incl. materialized views) | View queries and materialized acceleration |

The data layer is the core of an application package: after persistence the metadata cache is refreshed entity by entity, so model changes take effect hot.

## Business Data

Whether business data travels with the package is decided by the data-pack strategy configured per entity at modeling time (`platform_dev_table.pack_bus_data`):

| Value | Meaning | Packing | Deployment |
| --- | --- | --- | --- |
| `0` | Do not pack | Not in the package | — |
| `1` | Incremental | All rows of this app in that table | Target data is not cleared; rows with an existing id are skipped (idempotent merge) |
| `2` | Full | All rows of this app in that table | Target rows of this app are cleared, then package rows inserted |

Typical usage: incremental for dictionary-like seed data and base parameters; full for data that should migrate and be replaced with the app as a whole. The strategy is configured in the model designer's entity form.

## Governance Layer

| Table | Entity | Carried contents | Runtime role |
| --- | --- | --- | --- |
| `platform_role` | `Role` | Roles (`type` distinguishes app/platform level) | The principal of the authorization system |
| `platform_permission` | `Permission` | Permission points and data rules `rule` (MQL fragments) | `rule` is spliced into queries during reads/writes, implementing row-level data filtering |
| `platform_role_r_permission` | `RolePermissionMap` | Role-permission relations | Which permission points a role owns |
| `platform_role_r_tree_node` | `RoleTreeNodeMap` | Role-menu relations | Which menus a role sees |
| `platform_role_r_app` | `RoleAppMap` | Role-app relations | Which apps a user can enter |
| `platform_dict` / `platform_dict_item` | `Dict` / `DictItem` | Dictionaries and items | Front-end dropdown/tag rendering and value translation (`GET /api/system/dictionary/{code}`) |
| `platform_sys_config` | `SysConfig` | App config key-values | Merged with platform and tenant levels and served via `GET /api/config` |
| `platform_export_template` | `ExportTemplate` | Export templates | Format definitions for list/report exports |
| `platform_encoding` | `Encoding` | Encoding rules | Template-based generation of business numbers/codes |
| `platform_resources` | `Resources` | Resource metadata rows (path/name/type…) | Resource index; **not the file binary contents** (see below) |

## Not in the Package Manifest

:::warning Handle separately when migrating
The following are **NOT carried** by an application package:

| Contents | Tables | Description |
| --- | --- | --- |
| Service interfaces | `platform_api` | Standalone service-interface scripts (GraalJS) are not whitelisted; logic embedded in pages is unaffected, but pages depending on standalone interfaces will miss them after deployment |
| SQL orchestrations | `platform_sql` | Custom SQL orchestrations (`/api/sk/{key}`) |
| Scheduled tasks | `platform_schedule` | cron task definitions |
| File binaries | `platform_file` etc. | `platform_resources` carries only metadata rows; attachments/images must be migrated separately through file storage |
| Referenced contents of other apps | — | Entities/pages referenced across apps do not travel with this package (next section) |
:::

## Dependencies & Cross-App References

- Inter-app dependencies are declared via `platform_app.depend_app_code` (comma-separated app codes); packing does **no dependency analysis** — the contents of depended-on apps are not pulled into the package automatically, so the target environment must already contain them before deployment.
- Cross-app authorization mounts for models/views/SQL orchestrations (`platform_app_r_table/view/sql`) are not in the manifest; re-grant them in the target environment.
- Entity metadata registers globally by entity name (no app filtering), so cross-app entity references are directly accessible with access control left to permission rules; `platform_dev_table.across_app` is only a model-level flag.

## Further Reading

- [Packaging Flow](packaging-flow.md): how the above becomes a `.zgdp`
- [Deploying Application Packages](deployment-flow.md): how overwrite and incremental persistence apply these contents
- [Metadata Overview](../metadata/overview.md): parsing and loading of `EntityMeta`/`MetaManager`
