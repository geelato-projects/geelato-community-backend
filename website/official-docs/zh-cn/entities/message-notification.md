---
title: 消息与通知
sidebar_label: 消息与通知
---

# 消息与通知

本页覆盖站内消息、通知公告与通知流水线三套机制共 7 个实体。核心是通知三件套：Notification（主体，一封通知一行）+ NotificationUser（收件人状态，一人一行）+ NotificationOutbox（投递发件箱，一渠道一行），三者职责分离支撑多人独立已读与按渠道可靠投递；Message/Notice 是早期的单表消息与通知；UserEmailAccount 是用户外部邮箱接入配置。

## Message 消息

平台站内消息（早期单表形态）：一条消息带发送者、接收者与发送状态。继承 `BaseEntity`，公共字段见[概览](overview.md#公共基类字段)，未标注 catalog。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| appId | app_id | String | 应用 ID |
| title | title | String | 消息标题 |
| content | content | String | 消息内容 |
| receiver | receiver | String | 接收者 |
| sender | sender | String | 发送者 |
| sendTime | send_time | Date | 发送时间 |
| sendMethod | send_method | String | 发送方式 |
| sendType | send_type | String | 发送类别 |
| sendStatus | send_status | int | 发送状态：0 未发送、1 发送成功、2 发送失败、3 已读 |

## Notice 平台通知

通知公告（早期单表形态）：一条通知带收件人与业务关联。继承 `BaseEntity`，未标注 catalog。注意：本实体自行声明了 `deptId`/`buId`/`tenantCode` 三个字段，列名与 `BaseEntity` 基类字段相同（同列遮蔽）。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| receiver | receiver | String | 收件人 |
| noticeTitle | notice_title | String | 标题 |
| noticeContent | notice_content | String | 内容 |
| sender | sender | String | 发件人 |
| bizRef | biz_ref | String | 业务关联 |
| status | status | String | 状态 |
| deptId | dept_id | String | 部门信息 |
| buId | bu_id | String | 企业信息 |
| tenantCode | tenant_code | String | 租户编码 |

## Notification 平台通知

通知主体，一封通知一行。与 NotificationUser（收件人状态）分离：一封通知可发给多人，每人独立已读状态；与 NotificationOutbox（投递发件箱）分离：按渠道异步、可靠投递。继承 `BaseEntity`。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| title | title | String | 标题，非空，长度 128 |
| content | content | String | 内容 |
| senderId | sender_id | String | 发送者 ID（userId），系统发送为 `system`，长度 64 |
| senderName | sender_name | String | 发送者名称，长度 64 |
| senderType | sender_type | String | 发送者类型：`system` 或 `user`，长度 16 |
| bizType | biz_type | String | 业务类型，order/contract/task 等业务标识，长度 32 |
| bizId | biz_id | String | 业务主键，长度 64 |
| actionUrl | action_url | String | 跳转地址，点击通知跳转（前端 router.push 或 window.open），长度 512 |
| channels | channels | String | 投递渠道快照 JSON，如 `["inapp","email"]`，长度 256 |
| priority | priority | int | 重要级别：0 普通、1 提醒、2 重要、3 紧急 |

## NotificationUser 通知收件人状态

通知收件人状态，每个收件人一行，与通知主体分离使每人独立已读/星标/归档状态，互不影响，并支持按用户维度的收件箱查询。继承 `BaseEntity`。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| notificationId | notification_id | String | 通知 ID，非空，长度 32 |
| userId | user_id | String | 收件人 ID，非空，长度 64 |
| readStatus | read_status | int | 已读状态：0 未读、1 已读 |
| readAt | read_at | Date | 已读时间 |
| starred | starred | int | 星标：0 否、1 是 |
| archived | archived | int | 已归档：0 否、1 是（用户主动归档，默认收件箱不展示） |

## NotificationOutbox 通知投递发件箱

通知投递编排发件箱，每个投递渠道一行。事务性 outbox：业务事务内写入（ready），调度器异步扫描、抢占（CAS → processing）、调用对应 DeliveryChannel 投递，成功 → success，失败 → 指数退避重试 → 死信（dead）。单渠道失败不影响其他渠道。继承 `BaseEntity`。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| notificationId | notification_id | String | 通知 ID，非空，长度 32 |
| channel | channel | String | 投递渠道：`inapp` / `email` / `sms` / `wecom` 等，非空，长度 32 |
| recipientJson | recipient_json | String | 收件人 userId 列表 JSON，如 `["u1","u2"]`，非空，长度 2048 |
| status | status | String | 投递状态：`ready` / `processing` / `success` / `fail` / `dead`，长度 16 |
| retryCount | retry_count | int | 重试次数 |
| nextRetryAt | next_retry_at | Date | 下次重试时间 |
| errorMsg | error_msg | String | 错误信息，长度 512 |

## UserEmailAccount 用户邮箱账号

用户外部邮箱账户配置：IMAP 收件、SMTP 发件、OAuth2 与同步策略。继承 `BaseEntity`，实现 `EntityEnableAble`。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| userId | user_id | String | 用户 ID，非空 |
| emailAddress | email_address | String | 邮箱地址，非空 |
| displayName | display_name | String | 显示名称 |
| providerCode | provider_code | String | 邮箱厂商：qq/163/126/gmail/outlook/other |
| defaultFlag | default_flag | int | 默认邮箱：0 否、1 是 |
| imapHost | imap_host | String | IMAP Host |
| imapPort | imap_port | Integer | IMAP 端口 |
| imapSsl | imap_ssl | int | IMAP SSL：0 否、1 是 |
| imapFolderDefault | imap_folder_default | String | 默认文件夹 |
| authType | auth_type | String | 认证类型：auth_code/password/oauth2 |
| authUser | auth_user | String | 认证用户名 |
| authSecret | auth_secret | String | 认证密文（授权码/密码密文） |
| oauth2Json | oauth2_json | String | OAuth2 信息，JSON |
| smtpHost | smtp_host | String | SMTP Host |
| smtpPort | smtp_port | Integer | SMTP 端口 |
| smtpSsl | smtp_ssl | int | SMTP SSL：0 否、1 是 |
| smtpStarttls | smtp_starttls | int | SMTP STARTTLS：0 否、1 是 |
| smtpAuthUser | smtp_auth_user | String | SMTP 认证用户名 |
| smtpAuthSecret | smtp_auth_secret | String | SMTP 认证密文（授权码/密码密文） |
| smtpFromName | smtp_from_name | String | 发件显示名称 |
| signatureHtml | signature_html | String | 邮件签名，HTML |
| enableStatus | enable_status | int | 启用状态：1 启用、0 不启用 |
| syncEnabled | sync_enabled | int | 同步开关：0 关、1 开 |
| syncIntervalMinutes | sync_interval_minutes | Integer | 同步间隔（分钟），默认 5 分钟 |
| lastSyncAt | last_sync_at | Date | 上次同步时间 |
| syncStatus | sync_status | String | 同步状态：idle/syncing/error |

## UserStockMap 用户常用联系人

用户常用联系人关联。属性名与列名沿用"stock"字样，实际语义为常用联系人（通讯录收藏）。继承 `BaseEntity`，未标注 catalog。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| userId | user_id | String | 用户 ID |
| stockId | stock_id | String | 联系人 ID |
| appId | app_id | String | 应用 ID |

## 推荐继续阅读

- [全平台实体索引](platform-entity-index.md)——邮件模块（geelato-mail）自带的 Mail* 实体清单
- [业务告警](../alert/overview.md)——告警消息经通知流水线投递到各渠道
