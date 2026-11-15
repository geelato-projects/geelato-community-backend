---
title: Applications & Designer
sidebar_label: Applications & Designer
---

# Applications & Designer

This page covers 8 entities across application definition, version management, page collaboration, multi-language, and menu navigation. The application (App) is the aggregate root of the low-code platform; the page (AppPage) is the core artifact produced by the designer; pages support multi-user collaboration through a check-in/check-out mechanism; the multi-language entities carry i18n text for applications and pages; TreeNode and MenuItem carry navigation.

## App

Application definition — the aggregate root of the low-code platform: the runtime loads pages, menus and themes per application. Extends `BaseSortableEntity`; common fields are listed in [Overview](overview.md#common-base-class-fields).

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| name | name | String | Application name |
| code | code | String | Code |
| type | type | String | Application category |
| icon | icon | String | Icon |
| appKey | app_key | String | Application secret key |
| token | token | String | Application token |
| tree | tree | String | File tree, JSON string (e.g. jsTree) |
| logo | logo | String | Logo |
| theme | theme | String | Application theme |
| watermark | watermark | int | Application watermark, whether enabled, disabled by default |
| href | href | String | Home link, the home page opened after the module loads |
| dependAppCode | depend_app_code | String | Dependent application module codes, multiple allowed, e.g. `dev,sys` |
| powerInfo | power_info | String | Copyright info, the site statement at the page footer |
| versionInfo | version_info | String | Version info |
| description | description | String | Description |
| applyStatus | apply_status | int | Application site status: 1 enabled, 0 disabled |
| designStatus | design_status | int | Design site status: 1 enabled, 0 disabled |
| appStorage | app_storage | String | Application repository address |
| purpose | purpose | String | Application scope |
| ideVersion | ide_version | String | IDE version |
| modelColumnPcl | model_column_pcl | String | Model field permission check level: strict or loose |
| roles | — | String | `@Transient`, not persisted; query helper field |

## AppVersion

Application version management, recording the artifact address and status of each package build. Extends `BaseEntity`. See [Application Packaging](../packaging/overview.md) for how packages are produced.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| packagePath | package_path | String | Package address |
| version | version | String | Version name |
| packageSource | package_source | String | Package source |
| appId | app_id | String | Owning application |
| packetTime | packet_time | Date | Packaging time |
| status | status | String | Status |
| description | description | String | Description |

## AppPage

Page definition produced by the page designer; source, preview and release content are stored in separate columns; check-in/check-out fields support multi-user collaboration. Extends `BaseSortableEntity`.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| extendId | extend_id | String | Extension info, extension id (e.g. the corresponding leaf node id) |
| appId | app_id | String | Application id, owning application |
| type | type | String | Type, e.g. `api`/`form`/`table` |
| title | title | String | Title |
| code | code | String | Code |
| sourceContent | source_content | String | Source content, longText, non-null |
| previewContent | preview_content | String | Preview content, longText, non-null |
| releaseContent | release_content | String | Release content, longText, non-null |
| description | description | String | Description |
| version | version | int | Version |
| checkUserId | check_user_id | String | Check-in user id |
| checkUserName | check_user_name | String | Check-in user name |
| checkStatus | check_status | String | Check-in status: `unchecked`, `checkedOut`; matches `CheckStatusEnum` |
| checkAt | check_at | Date | Check-in time |

## AppPageCheckReq

Page check-out request / takeover request entity: when a page is checked out by someone else, a takeover request is filed through this entity and tracked to approval. Extends `BaseEntity`.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| pageId | page_id | String | Page id, non-null |
| pageTitle | page_title | String | Page title, non-null |
| requesterId | requester_id | String | Requester user id, non-null |
| requesterName | requester_name | String | Requester user name, non-null |
| targetUserId | target_user_id | String | Target user id (current holder of the check-out), non-null |
| targetUserName | target_user_name | String | Target user name, non-null |
| requestTime | request_time | Date | Request time, non-null |
| status | status | String | Request status: `pending`/`approved`/`rejected`/`expired`, non-null |
| processTime | process_time | Date | Process time |
| processUserId | process_user_id | String | Processing user id |
| processUserName | process_user_name | String | Processing user name |
| processComment | process_comment | String | Processing comment, varchar(500) |

## AppMultiLang

Application-level multi-language text: one language pack per row. Extends `BaseEntity`. Carries an `enable_status` field (without implementing `EntityEnableAble`).

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| appId | app_id | String | Application id |
| langType | lang_type | String | Language type |
| langPackage | lang_package | String | Language package |
| purpose | purpose | String | Language scope |
| enableStatus | enable_status | int | Enable status: 1 enabled, 0 disabled |

## AppPageLang

Page-level multi-language text, stored per page version. Extends `BaseEntity`.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| pageId | page_id | String | Owning page |
| version | version | int | Version |
| langType | lang_type | String | Language type |
| content | content | String | Multi-language content |
| extendId | extend_id | String | Menu tree node id |
| appId | app_id | String | Owning application |

## TreeNode

Generic tree node entity; its class title is "菜单" (menu): it carries both the runtime menu tree and any business tree such as the designer's file tree (attached via treeEntity/treeId). Extends `BaseSortableEntity`.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| treeEntity | tree_entity | String | Tree entity: the business entity the node's tree belongs to, e.g. a project file tree corresponds to `platform_project` |
| treeId | tree_id | String | Tree id: the id of a record of the tree's business entity, e.g. the project id; non-null |
| pid | pid | String | Parent node id, string format, avoiding display distortion of overlarge numbers in web browsers |
| type | type | String | Node type |
| icon | icon | String | Node icon |
| text | text | String | Node title |
| iconType | icon_type | String | Node icon type |
| extendEntity | extend_entity | String | Extension entity, e.g. the file/business table name of a leaf node |
| extendId | extend_id | String | Extension entity id, e.g. the file/form id of a leaf node |
| meta | meta | String | Node extension metadata, JSON string |
| flag | flag | String | Signal flag for special marking of the node, e.g. as a menu item |
| url | url | String | Link, opens a third-party URL from the menu item |
| description | description | String | Description |

## MenuItem

Menu item and sub-menu item definition; links to a designer page or a tree node. Extends `BaseSortableEntity`.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| title | title | String | Title |
| clazz | clazz | String | CSS class |
| active | active | String | Active |
| linkType | link_type | String | Link type: `dynamicPage` (a page configured in the designer) or `other`, length 20 |
| href | href | String | Link |
| pageCode | page_code | String | Linked page, the page code opened by the link |
| treeNodeId | tree_node_id | String | Tree node id, referencing TreeNode |

## Recommended Reading

- [API & Orchestration Authorization](api-orchestration.md) — the capability outlet of an application: API services and custom orchestration
- [Roles & Permissions](role-permission.md) — authorization of applications to roles and menus to roles
- [Application Packaging](../packaging/overview.md) — where AppVersion packages come from
