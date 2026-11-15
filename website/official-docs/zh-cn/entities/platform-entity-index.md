---
title: 全平台实体索引
sidebar_label: 全平台实体索引
---

# 全平台实体索引

本页是平台全部运行期实体的总索引：回答"平台到底存在哪些实体元数据"。平台实体的定义分布在多个模块——核心实体集中在 `geelato-meta`，特性模块（业务告警、业务异常、邮件、平台内置能力）各自定义自己的实体，注册机制相同：都在 `cn.geelato` 包下，由 `MetaManager` 启动扫描自动注册（见[概览](overview.md)）。

**不纳入本索引的"实体"**：

- 框架元数据模型类（`geelato-core` 的 TableMeta、ColumnMeta、TableView、TableCheck、TableForeign、ConnectMeta）——它们虽也标注 `@Entity`，但承载的是"元数据的定义"本身，映射到 `platform_dev_*` 设计器六表，见[元数据说明](../metadata/overview.md)。
- `geelato-maintenance` 的 MqlTest* 样例模型（MqlTestUser/MqlTestOrder/MqlTestOrderItem 等）——MQL 测试夹具，非运行期实体。

## geelato-meta 核心实体（54 个）

实体类名即链接，指向本章对应实体条目（含全字段表）。

| 实体类 | 表名 | 业务域 | 说明 |
| --- | --- | --- | --- |
| [App](app-and-designer.md#app-应用) | platform_app | 应用与设计器 | 低代码应用定义（聚合根） |
| [AppVersion](app-and-designer.md#appversion-应用版本) | platform_app_version | 应用与设计器 | 应用版本与包地址 |
| [AppPage](app-and-designer.md#apppage-页面) | platform_app_page | 应用与设计器 | 页面定义（源码/预览/发布三态 + 签入签出） |
| [AppPageCheckReq](app-and-designer.md#apppagecheckreq-页面签出申请接管请求) | platform_app_page_check_req | 应用与设计器 | 页面签出申请/接管请求 |
| [AppMultiLang](app-and-designer.md#appmultilang-应用多语言) | platform_app_multi_lang | 应用与设计器 | 应用级多语言文案 |
| [AppPageLang](app-and-designer.md#apppagelang-页面多语言) | platform_app_page_lang | 应用与设计器 | 页面级多语言文案 |
| [TreeNode](app-and-designer.md#treenode-菜单) | platform_tree_node | 应用与设计器 | 通用树节点（菜单树/文件树） |
| [MenuItem](app-and-designer.md#menuitem-菜单项) | platform_menu_item | 应用与设计器 | 菜单项/子菜单项 |
| [Api](api-orchestration.md#api-服务接口) | platform_api | 接口与编排授权 | 服务接口定义 |
| [ApiParam](api-orchestration.md#apiparam-接口服务参数) | platform_api_param | 接口与编排授权 | 接口入参/出参定义 |
| [CustomSql](api-orchestration.md#customsql-自定义接口编排) | platform_sql | 接口与编排授权 | 自定义接口编排 |
| [AppSqlMap](api-orchestration.md#appsqlmap-应用接口编排关系表) | platform_app_r_sql | 接口与编排授权 | 应用-编排授权关系 |
| [AppTableMap](api-orchestration.md#apptablemap-应用模型授权关系表) | platform_app_r_table | 接口与编排授权 | 应用-模型授权关系 |
| [AppViewMap](api-orchestration.md#appviewmap-应用视图授权关系表) | platform_app_r_view | 接口与编排授权 | 应用-视图授权关系 |
| [ExportTemplate](api-orchestration.md#exporttemplate-导出文件模板) | platform_export_template | 接口与编排授权 | 导出文件模板 |
| [Org](org-tenant-user.md#org-组织) | platform_org | 组织与用户 | 组织机构树 |
| [Company](org-tenant-user.md#company-公司信息) | platform_company | 组织与用户 | 法人公司信息 |
| [User](org-tenant-user.md#user-用户) | platform_user | 组织与用户 | 用户账号主体 |
| [Area](org-tenant-user.md#area-区县) | platform_area | 组织与用户 | 行政区划（省市区县） |
| [Tenant](org-tenant-user.md#tenant-租户) | platform_tenant | 组织与用户 | 租户定义 |
| [TenantSite](org-tenant-user.md#tenantsite-租户站点) | platform_tenant_site | 组织与用户 | 租户站点配置 |
| [Role](role-permission.md#role-角色) | platform_role | 角色与权限 | 角色定义（应用级/平台级） |
| [Permission](role-permission.md#permission-权限项) | platform_permission | 角色与权限 | 权限点定义 |
| [RoleUserMap](role-permission.md#roleusermap-角色用户关系表) | platform_role_r_user | 角色与权限 | 角色-用户直挂 |
| [OrgRoleMap](role-permission.md#orgrolemap-组织角色关系表) | platform_org_r_role | 角色与权限 | 角色-组织挂靠 |
| [OrgUserMap](role-permission.md#orgusermap-组织用户关系表) | platform_org_r_user | 角色与权限 | 用户-组织成员关系 |
| [UserOrgRoleMap](role-permission.md#userorgrolemap-用户组织角色关系表) | platform_user_r_org_role | 角色与权限 | 用户×组织×角色直挂 |
| [RolePermissionMap](role-permission.md#rolepermissionmap-角色权限关系表) | platform_role_r_permission | 角色与权限 | 角色-权限关联 |
| [UserPermissionMap](role-permission.md#userpermissionmap-用户权限关系表) | platform_user_r_permission | 角色与权限 | 用户直授权限 |
| [RoleAppMap](role-permission.md#roleappmap-角色app关系表) | platform_role_r_app | 角色与权限 | 角色-可访问应用 |
| [RoleTreeNodeMap](role-permission.md#roletreenodemap-角色菜单关系表) | platform_role_r_tree_node | 角色与权限 | 角色-菜单授权 |
| [UserDelegateMap](role-permission.md#userdelegatemap-用户委托关系表) | platform_user_r_delegate | 角色与权限 | 用户委托/助理关系 |
| [Message](message-notification.md#message-消息) | platform_message | 消息与通知 | 站内消息（早期单表） |
| [Notice](message-notification.md#notice-平台通知) | platform_notice | 消息与通知 | 通知公告（早期单表） |
| [Notification](message-notification.md#notification-平台通知) | platform_notification | 消息与通知 | 通知主体（一封一行） |
| [NotificationUser](message-notification.md#notificationuser-通知收件人状态) | platform_notification_user | 消息与通知 | 通知收件人状态（一人一行） |
| [NotificationOutbox](message-notification.md#notificationoutbox-通知投递发件箱) | platform_notification_outbox | 消息与通知 | 通知投递发件箱（一渠道一行） |
| [UserEmailAccount](message-notification.md#useremailaccount-用户邮箱账号) | platform_user_email_account | 消息与通知 | 用户外部邮箱账户配置 |
| [UserStockMap](message-notification.md#userstockmap-用户常用联系人) | platform_user_r_stock | 消息与通知 | 用户常用联系人 |
| [Dict](dict-config-file.md#dict-数据字典) | platform_dict | 字典配置与文件 | 数据字典定义 |
| [DictItem](dict-config-file.md#dictitem-数据字典项) | platform_dict_item | 字典配置与文件 | 数据字典项 |
| [SysConfig](dict-config-file.md#sysconfig-系统配置) | platform_sys_config | 字典配置与文件 | 应用参数配置（键值对） |
| [Qrcode](dict-config-file.md#qrcode-二维码配置) | platform_qrcode | 字典配置与文件 | 二维码样式与内容配置 |
| [FileInfo](dict-config-file.md#fileinfo-文件) | platform_file | 字典配置与文件 | 文件记录 |
| [Attach](dict-config-file.md#attach-附件) | platform_attach | 字典配置与文件 | 附件表 |
| [Compress](dict-config-file.md#compress-压缩文件) | platform_compress | 字典配置与文件 | 压缩包文件表 |
| [Resources](dict-config-file.md#resources-资源信息) | platform_resources | 字典配置与文件 | 静态资源文件表 |
| [Schedule](schedule-integration-log.md#schedule-调度任务) | platform_schedule | 调度集成与日志 | 定时调度任务定义 |
| [OrmHook](schedule-integration-log.md#ormhook-orm实体钩子配置) | platform_orm_hook | 调度集成与日志 | 实体事件钩子配置 |
| [AppPageLog](schedule-integration-log.md#apppagelog-页面操作日志) | platform_app_page_log | 调度集成与日志 | 页面操作日志 |
| [ScheduleLog](schedule-integration-log.md#schedulelog-调度日志) | platform_schedule_log | 调度集成与日志 | 调度执行日志 |
| [OrmHookLog](schedule-integration-log.md#ormhooklog-orm钩子执行发件箱) | platform_orm_hook_log | 调度集成与日志 | 钩子投递发件箱 |
| [EncodingLog](schedule-integration-log.md#encodinglog-编码记录) | platform_encoding_log | 调度集成与日志 | 单据编码/流水号记录（catalog=email） |
| [PlatformExceptionLog](schedule-integration-log.md#platformexceptionlog-平台错误日志) | platform_exception_log | 调度集成与日志 | 平台错误日志（catalog=platform-log） |

## 业务告警模块（geelato-alert，4 个）

catalog 均为 `geelato`。字段级说明见[业务告警](../alert/overview.md)。

| 实体类 | 表名 | 说明 |
| --- | --- | --- |
| FmsAlertRule | platform_alert_rule | 告警规则 |
| FmsAlert | platform_alert | 告警 |
| FmsAlertSilence | platform_alert_silence | 告警静默规则 |
| FmsAlertTemplate | platform_alert_template | 告警消息模板 |

## 业务异常模块（geelato-exception，5 个）

catalog 均为 `geelato`。字段级说明见[业务异常](../exception/overview.md)。

| 实体类 | 表名 | 说明 |
| --- | --- | --- |
| FmsExceptionRule | platform_exception_rule | 异常规则 |
| FmsException | platform_exception | 异常实例 |
| FmsExceptionHandle | platform_exception_handle | 异常处理轨迹 |
| FmsExceptionStep | platform_exception_step | 异常处置步骤 |
| FmsExceptionScanLog | platform_exception_scan_log | 异常扫描日志 |

## 邮件模块（geelato-mail，13 个）

catalog 均为 `mail`，表建在邮件实体实际路由到的数据源。邮件模块未设独立文档章节，实体清单如下（说明按类名语义归纳）：

| 实体类 | 表名 | 说明 |
| --- | --- | --- |
| MailAccount | mail_account | 邮箱账户 |
| MailMessage | mail_message | 邮件消息 |
| MailSetting | mail_setting | 邮件设置 |
| MailSignature | mail_signature | 邮件签名 |
| MailLabel | mail_label | 邮件标签 |
| MailFolderCustom | mail_folder_custom | 自定义邮件文件夹 |
| MailFilter | mail_filter | 邮件过滤规则 |
| MailFilterApplyLog | mail_filter_apply_log | 过滤规则应用日志 |
| MailVacation | mail_vacation | 假期/自动回复配置 |
| MailAutoReplyLog | mail_auto_reply_log | 自动回复日志 |
| MailContact | mail_contact | 联系人 |
| MailContactGroup | mail_contact_group | 联系人分组 |
| MailContactRecent | mail_contact_recent | 最近联系人 |

## 平台内置能力实体（geelato-web-platform，6 个）

OCR、条形码、编码、站点、审计等平台内置能力的实体，随平台服务定义：

| 实体类 | 表名 | catalog | 说明 |
| --- | --- | --- | --- |
| OcrPdf | platform_ocr_pdf | 默认 | OCR 解析 PDF 记录 |
| OcrPdfMeta | platform_ocr_pdf_meta | 默认 | OCR PDF 元数据 |
| Barcode | platform_barcode | 默认 | 条形码配置 |
| Encoding | platform_encoding | 默认 | 编码规则 |
| StaticSite | platform_static_site | 默认 | 静态站点 |
| AuditLogRecord | platform_audit_log | platform | 平台业务审计日志 |

## 推荐继续阅读

- [平台实体总览](overview.md)——geelato-meta 模块的公共基类字段与注册机制
- [元数据说明](../metadata/overview.md)——实体元数据的解析、缓存与消费机制
