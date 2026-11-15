---
title: API & Orchestration Authorization
sidebar_label: API & Orchestration
---

# API & Orchestration Authorization

This page covers 7 entities across API services, custom orchestration, export templates and application authorization relations. Api defines the service interfaces an application exposes; CustomSql is code-free API orchestration; the three `App*Map` relation tables authorize applications against models, views and orchestrations; ExportTemplate supports file export.

## Api

Definition of the service interfaces an application exposes: APIs of the MQL/orchestration/JS script kinds, with controls for anonymous access, paging and third-party openness. Extends `BaseEntity`; common fields are listed in [Overview](overview.md#common-base-class-fields).

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| appId | app_id | String | Owning application |
| name | name | String | Name |
| code | code | String | Code |
| paging | paging | boolean | Whether paginated, defaults to false |
| method | method | String | HTTP method |
| remark | remark | String | Remark |
| version | version | Integer | Version |
| anonymous | anonymous | int | Anonymous access: 1 allowed without auth, 0 not allowed, auth required; defaults to 0 |
| groupName | group_name | String | Group name |
| enableStatus | enable_status | int | Whether enabled: 0 disabled, 1 enabled; controls access to the API service; defaults to 1 |
| sourceContent | source_content | String | GlPage component tree string |
| releaseContent | release_content | String | Generated executable JavaScript script |
| outsideUrl | outside_url | String | Third-party access URL |
| outsideStatus | outside_status | int | Third-party access status, defaults to 0 |
| responseFormat | response_format | String | Response format |
| responseType | response_type | String | Response parameter type |
| logLevel | log_level | String | Log level |
| requestParams | — | List\<ApiParam\> | `@Transient`, not persisted; request parameter tree, see ApiParam |
| responseParams | — | List\<ApiParam\> | `@Transient`, not persisted; response parameter tree, see ApiParam |

## ApiParam

Request/response parameter definition of an API, organized into a parameter tree by pid. Extends `BaseEntity`.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| appId | app_id | String | Owning application |
| apiId | api_id | String | Owning API |
| pid | pid | String | Parent parameter id, forming the parameter hierarchy |
| paramType | param_type | String | Parameter type (request/response grouping) |
| bodyType | body_type | String | Type |
| name | name | String | Name |
| dataType | data_type | String | Parameter data type |
| defaultValue | default_value | String | Parameter default value |
| demoValue | demo_value | String | Demo data |
| alternateType | alternate_type | String | Interaction type |
| required | required | boolean | Whether required, defaults to false |
| jacket | jacket | boolean | Not documented in source; defaults to false |
| remark | remark | String | Remark |
| children | — | List\<ApiParam\> | `@Transient`, not persisted; child parameter tree |

## CustomSql

Code-free API orchestration definition (`platform_sql`): declares request/response parameters and orchestration content; the key name is the call identifier. Extends `BaseEntity`. Carries an `enable_status` field without implementing `EntityEnableAble`.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| appId | app_id | String | Owning application |
| title | title | String | Title |
| keyName | key_name | String | Key name |
| requestParams | request_params | String | Request parameters |
| responseType | response_type | String | Response parameter type |
| responseParams | response_params | String | Response parameters |
| description | description | String | Description |
| enableStatus | enable_status | int | Whether enabled: 0 disabled, 1 enabled |
| configType | config_type | String | Configuration type |
| encodingContent | encoding_content | String | Encoded content; auto-decoded on save when it is a base64 data URI (`afterSet`) |

## AppSqlMap

Authorization relation between an application and an orchestration (CustomSql). The word "编码" in the class title "应用接口编码关系表" refers to orchestration (sqlKey/sqlTitle both point to CustomSql). Extends `BaseEntity`. Carries an `enable_status` field without implementing `EntityEnableAble`.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| appId | app_id | String | Requesting application id |
| appName | app_name | String | Requesting application name |
| sqlId | sql_id | String | Orchestration id, referencing CustomSql |
| sqlTitle | sql_title | String | Orchestration title |
| sqlKey | sql_key | String | Orchestration key name |
| sqlAppId | sql_app_id | String | Application the orchestration belongs to |
| approvalNeed | approval_need | boolean | Whether approval is required, defaults to false |
| approvalStatus | approval_status | String | Approval status |
| enableStatus | enable_status | int | Whether enabled: 0 disabled, 1 enabled |
| description | description | String | Description |

## AppTableMap

Authorization relation between an application and a data model (designer table definition). Extends `BaseEntity`.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| appId | app_id | String | Application id, `@ForeignKey` to App (platform_app) |
| appName | app_name | String | Application name, reference column (isRefColumn, fetched from platform_app.name by appId) |
| tableId | table_id | String | Model id, `@ForeignKey` to TableMeta (platform_dev_table, designer table definition) |
| tableName | table_name | String | Model name |
| tableTitle | table_title | String | Model title |
| tableAppId | table_app_id | String | Application the model belongs to |
| permissionId | permission_id | String | Permission id, referencing Permission |
| permissionName | permission_name | String | Permission name |
| approvalNeed | approval_need | boolean | Whether approval is required, defaults to false |
| approvalStatus | approval_status | String | Approval status |
| enableStatus | enable_status | int | Whether enabled: 0 disabled, 1 enabled |
| description | description | String | Description |

## AppViewMap

Authorization relation between an application and a view. Extends `BaseEntity`.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| appId | app_id | String | Application id, `@ForeignKey` to App (platform_app) |
| appName | app_name | String | Application name, reference column (isRefColumn) |
| tableName | table_name | String | Model name (the base table of the view) |
| viewId | view_id | String | View id |
| viewName | view_name | String | View name |
| viewTitle | view_title | String | View title |
| viewAppId | view_app_id | String | Application the view belongs to |
| permissionId | permission_id | String | Permission id |
| permissionName | permission_name | String | Permission name. The source @Col and @Title of this field are misaligned by copy-paste (title says "whether enabled"); documented here by actual semantics |
| approvalNeed | approval_need | boolean | Whether approval is required, defaults to false |
| approvalStatus | approval_status | String | Approval status |
| enableStatus | enable_status | int | Whether enabled: 0 disabled, 1 enabled |
| description | description | String | Description |

## ExportTemplate

Export file template definition: templates are configured by file type and purpose, with up to 19 template slots (fetched by `indexTemplate` for 1–19), carrying business data type, cleansing rules, metadata and visual layout JSON. Extends `BaseEntity`, implements `EntityEnableAble`. Note: field-level @Title in this entity is massively copy-pasted as "状态" (status); the table below documents actual semantics from field names and inline comments.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| appId | app_id | String | Owning application (source @Title mislabeled "status") |
| title | title | String | Template title (source @Title mislabeled "status") |
| useType | use_type | String | Purpose |
| fileType | file_type | String | File type |
| fileCode | file_code | String | File code |
| template | template | String | Template content, slot 1 |
| templateRule | template_rule | String | Template rule |
| enableStatus | enable_status | int | Status: 0 disabled, 1 enabled |
| description | description | String | Description |
| businessTypeData | business_type_data | String | Data type |
| businessRuleData | business_rule_data | String | Cleansing rules |
| businessMetaData | business_meta_data | String | Metadata |
| canvasLayoutData | canvas_layout_data | String | WYSIWYG visual layout JSON |
| template2~template19 | template2~template19 | String | Template slots 2–19, fetched by `indexTemplate(index)` |

## Recommended Reading

- [Applications & Designer](app-and-designer.md) — where the authorized applications and pages are defined
- [Roles & Permissions](role-permission.md) — the Permission and role authorization chain
- [Metadata Overview](../metadata/overview.md) — the metadata mechanism behind platform_dev_* tables and TableMeta
