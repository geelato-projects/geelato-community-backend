---
title: Platform Entity Index
sidebar_label: Entity Index
---

# Platform Entity Index

This page is the master index of all runtime entities in the platform: it answers "which entity metadata actually exists in the platform". Entity definitions are spread across modules — core entities are centralized in `geelato-meta`, while feature modules (business alerting, business exceptions, mail, platform built-in capabilities) define their own entities. Registration is identical everywhere: they all live under the `cn.geelato` package and are auto-registered by the `MetaManager` startup scan (see [Overview](overview.md)).

**Entities not included in this index**:

- Framework meta-model classes (TableMeta, ColumnMeta, TableView, TableCheck, TableForeign, ConnectMeta in `geelato-core`) — although also annotated `@Entity`, they carry the definition of metadata itself, mapped to the six `platform_dev_*` designer tables; see [Metadata Overview](../metadata/overview.md).
- The MqlTest* sample models of `geelato-maintenance` (MqlTestUser/MqlTestOrder/MqlTestOrderItem etc.) — MQL test fixtures, not runtime entities.

## Core entities of geelato-meta (54)

The entity class names link to the corresponding entity entry in this chapter (with full field tables).

| Entity | Table | Domain | Description |
| --- | --- | --- | --- |
| [App](app-and-designer.md#app) | platform_app | Applications & Designer | Low-code application definition (aggregate root) |
| [AppVersion](app-and-designer.md#appversion) | platform_app_version | Applications & Designer | Application version and package address |
| [AppPage](app-and-designer.md#apppage) | platform_app_page | Applications & Designer | Page definition (source/preview/release + check-in/check-out) |
| [AppPageCheckReq](app-and-designer.md#apppagecheckreq) | platform_app_page_check_req | Applications & Designer | Page check-out request / takeover request |
| [AppMultiLang](app-and-designer.md#appmultilang) | platform_app_multi_lang | Applications & Designer | Application-level multi-language text |
| [AppPageLang](app-and-designer.md#apppagelang) | platform_app_page_lang | Applications & Designer | Page-level multi-language text |
| [TreeNode](app-and-designer.md#treenode) | platform_tree_node | Applications & Designer | Generic tree node (menu tree / file tree) |
| [MenuItem](app-and-designer.md#menuitem) | platform_menu_item | Applications & Designer | Menu item / sub-menu item |
| [Api](api-orchestration.md#api) | platform_api | API & Orchestration | Service interface definition |
| [ApiParam](api-orchestration.md#apiparam) | platform_api_param | API & Orchestration | API request/response parameter definition |
| [CustomSql](api-orchestration.md#customsql) | platform_sql | API & Orchestration | Custom API orchestration |
| [AppSqlMap](api-orchestration.md#appsqlmap) | platform_app_r_sql | API & Orchestration | Application-orchestration authorization relation |
| [AppTableMap](api-orchestration.md#apptablemap) | platform_app_r_table | API & Orchestration | Application-model authorization relation |
| [AppViewMap](api-orchestration.md#appviewmap) | platform_app_r_view | API & Orchestration | Application-view authorization relation |
| [ExportTemplate](api-orchestration.md#exporttemplate) | platform_export_template | API & Orchestration | Export file template |
| [Org](org-tenant-user.md#org) | platform_org | Organization & Users | Organization tree |
| [Company](org-tenant-user.md#company) | platform_company | Organization & Users | Legal-entity company information |
| [User](org-tenant-user.md#user) | platform_user | Organization & Users | User account principal |
| [Area](org-tenant-user.md#area) | platform_area | Organization & Users | Administrative divisions (province/city/district) |
| [Tenant](org-tenant-user.md#tenant) | platform_tenant | Organization & Users | Tenant definition |
| [TenantSite](org-tenant-user.md#tenantsite) | platform_tenant_site | Organization & Users | Tenant site configuration |
| [Role](role-permission.md#role) | platform_role | Roles & Permissions | Role definition (application/platform level) |
| [Permission](role-permission.md#permission) | platform_permission | Roles & Permissions | Permission point definition |
| [RoleUserMap](role-permission.md#roleusermap) | platform_role_r_user | Roles & Permissions | Direct role-user attachment |
| [OrgRoleMap](role-permission.md#orgrolemap) | platform_org_r_role | Roles & Permissions | Role attachment to organizations |
| [OrgUserMap](role-permission.md#orgusermap) | platform_org_r_user | Roles & Permissions | User-organization membership |
| [UserOrgRoleMap](role-permission.md#userorgrolemap) | platform_user_r_org_role | Roles & Permissions | User × organization × role direct attachment |
| [RolePermissionMap](role-permission.md#rolepermissionmap) | platform_role_r_permission | Roles & Permissions | Role-permission relation |
| [UserPermissionMap](role-permission.md#userpermissionmap) | platform_user_r_permission | Roles & Permissions | Permissions granted directly to users |
| [RoleAppMap](role-permission.md#roleappmap) | platform_role_r_app | Roles & Permissions | Role-accessible application relation |
| [RoleTreeNodeMap](role-permission.md#roletreenodemap) | platform_role_r_tree_node | Roles & Permissions | Role-menu authorization |
| [UserDelegateMap](role-permission.md#userdelegatemap) | platform_user_r_delegate | Roles & Permissions | User delegation/assistant relation |
| [Message](message-notification.md#message) | platform_message | Messages & Notifications | In-app message (earlier single-table form) |
| [Notice](message-notification.md#notice) | platform_notice | Messages & Notifications | Notice (earlier single-table form) |
| [Notification](message-notification.md#notification) | platform_notification | Messages & Notifications | Notification subject (one per notification) |
| [NotificationUser](message-notification.md#notificationuser) | platform_notification_user | Messages & Notifications | Notification recipient status (one per recipient) |
| [NotificationOutbox](message-notification.md#notificationoutbox) | platform_notification_outbox | Messages & Notifications | Notification delivery outbox (one per channel) |
| [UserEmailAccount](message-notification.md#useremailaccount) | platform_user_email_account | Messages & Notifications | User external mailbox configuration |
| [UserStockMap](message-notification.md#userstockmap) | platform_user_r_stock | Messages & Notifications | User frequent contacts |
| [Dict](dict-config-file.md#dict) | platform_dict | Dictionary, Config & Files | Data dictionary definition |
| [DictItem](dict-config-file.md#dictitem) | platform_dict_item | Dictionary, Config & Files | Data dictionary item |
| [SysConfig](dict-config-file.md#sysconfig) | platform_sys_config | Dictionary, Config & Files | Application parameter config (key-value) |
| [Qrcode](dict-config-file.md#qrcode) | platform_qrcode | Dictionary, Config & Files | QR code style and content configuration |
| [FileInfo](dict-config-file.md#fileinfo) | platform_file | Dictionary, Config & Files | File record |
| [Attach](dict-config-file.md#attach) | platform_attach | Dictionary, Config & Files | Attachment table |
| [Compress](dict-config-file.md#compress) | platform_compress | Dictionary, Config & Files | Compressed-archive file table |
| [Resources](dict-config-file.md#resources) | platform_resources | Dictionary, Config & Files | Static resource file table |
| [Schedule](schedule-integration-log.md#schedule) | platform_schedule | Scheduling & Logs | Scheduled task definition |
| [OrmHook](schedule-integration-log.md#ormhook) | platform_orm_hook | Scheduling & Logs | Entity event hook configuration |
| [AppPageLog](schedule-integration-log.md#apppagelog) | platform_app_page_log | Scheduling & Logs | Page operation log |
| [ScheduleLog](schedule-integration-log.md#schedulelog) | platform_schedule_log | Scheduling & Logs | Schedule execution log |
| [OrmHookLog](schedule-integration-log.md#ormhooklog) | platform_orm_hook_log | Scheduling & Logs | Hook delivery outbox |
| [EncodingLog](schedule-integration-log.md#encodinglog) | platform_encoding_log | Scheduling & Logs | Document number/serial record (catalog=email) |
| [PlatformExceptionLog](schedule-integration-log.md#platformexceptionlog) | platform_exception_log | Scheduling & Logs | Platform error log (catalog=platform-log) |

## Business Alerting module (geelato-alert, 4)

All with catalog `geelato`. Field-level descriptions: [Business Alerting](../alert/overview.md).

| Entity | Table | Description |
| --- | --- | --- |
| FmsAlertRule | platform_alert_rule | Alert rule |
| FmsAlert | platform_alert | Alert |
| FmsAlertSilence | platform_alert_silence | Alert silence rule |
| FmsAlertTemplate | platform_alert_template | Alert message template |

## Business Exceptions module (geelato-exception, 5)

All with catalog `geelato`. Field-level descriptions: [Business Exceptions](../exception/overview.md).

| Entity | Table | Description |
| --- | --- | --- |
| FmsExceptionRule | platform_exception_rule | Exception rule |
| FmsException | platform_exception | Exception instance |
| FmsExceptionHandle | platform_exception_handle | Exception handling trajectory |
| FmsExceptionStep | platform_exception_step | Exception handling step |
| FmsExceptionScanLog | platform_exception_scan_log | Exception scan log |

## Mail module (geelato-mail, 13)

All with catalog `mail`; tables live in the datasource the mail entities route to. The mail module has no standalone documentation chapter; the entity list follows (descriptions summarized from class names):

| Entity | Table | Description |
| --- | --- | --- |
| MailAccount | mail_account | Mail account |
| MailMessage | mail_message | Mail message |
| MailSetting | mail_setting | Mail settings |
| MailSignature | mail_signature | Mail signature |
| MailLabel | mail_label | Mail label |
| MailFolderCustom | mail_folder_custom | Custom mail folder |
| MailFilter | mail_filter | Mail filter rule |
| MailFilterApplyLog | mail_filter_apply_log | Filter application log |
| MailVacation | mail_vacation | Vacation/auto-reply configuration |
| MailAutoReplyLog | mail_auto_reply_log | Auto-reply log |
| MailContact | mail_contact | Contact |
| MailContactGroup | mail_contact_group | Contact group |
| MailContactRecent | mail_contact_recent | Recent contacts |

## Platform built-in capability entities (geelato-web-platform, 6)

Entities of the platform's built-in capabilities such as OCR, barcodes, encoding, static sites and auditing:

| Entity | Table | catalog | Description |
| --- | --- | --- | --- |
| OcrPdf | platform_ocr_pdf | default | OCR parsed PDF record |
| OcrPdfMeta | platform_ocr_pdf_meta | default | OCR PDF metadata |
| Barcode | platform_barcode | default | Barcode configuration |
| Encoding | platform_encoding | default | Encoding rule |
| StaticSite | platform_static_site | default | Static site |
| AuditLogRecord | platform_audit_log | platform | Platform business audit log |

## Recommended Reading

- [Platform Entities Overview](overview.md) — common base-class fields and registration of the geelato-meta module
- [Metadata Overview](../metadata/overview.md) — how entity metadata is parsed, cached and consumed
