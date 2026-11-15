---
title: Messages & Notifications
sidebar_label: Messages & Notifications
---

# Messages & Notifications

This page covers 7 entities across three mechanisms: in-app messages, notices and the notification pipeline. The core is the notification trio: Notification (the subject, one notification per row) + NotificationUser (recipient status, one row per recipient) + NotificationOutbox (delivery outbox, one row per channel); their separated responsibilities support independent read states for multiple recipients and reliable per-channel delivery. Message/Notice are the earlier single-table message and notice; UserEmailAccount is the user's external mailbox configuration.

## Message

Platform in-app message (earlier single-table form): one message with sender, receiver and send status. Extends `BaseEntity`; common fields are listed in [Overview](overview.md#common-base-class-fields); no catalog specified.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| appId | app_id | String | Application id |
| title | title | String | Message title |
| content | content | String | Message content |
| receiver | receiver | String | Receiver |
| sender | sender | String | Sender |
| sendTime | send_time | Date | Send time |
| sendMethod | send_method | String | Send method |
| sendType | send_type | String | Send category |
| sendStatus | send_status | int | Send status: 0 not sent, 1 sent, 2 failed, 3 read |

## Notice

Platform notice (earlier single-table form): one notice with receiver and business reference. Extends `BaseEntity`; no catalog specified. Note: this entity declares its own `deptId`/`buId`/`tenantCode` fields whose column names collide with the `BaseEntity` base-class fields (same-column shadowing).

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| receiver | receiver | String | Receiver |
| noticeTitle | notice_title | String | Title |
| noticeContent | notice_content | String | Content |
| sender | sender | String | Sender |
| bizRef | biz_ref | String | Business reference |
| status | status | String | Status |
| deptId | dept_id | String | Department info |
| buId | bu_id | String | Enterprise info |
| tenantCode | tenant_code | String | Tenant code |

## Notification

The notification subject, one notification per row. Separated from NotificationUser (recipient status): one notification can be sent to many people, each with an independent read state; separated from NotificationOutbox (delivery outbox): asynchronous, reliable delivery per channel. Extends `BaseEntity`.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| title | title | String | Title, non-null, length 128 |
| content | content | String | Content |
| senderId | sender_id | String | Sender id (userId); `system` when sent by the system, length 64 |
| senderName | sender_name | String | Sender name, length 64 |
| senderType | sender_type | String | Sender type: `system` or `user`, length 16 |
| bizType | biz_type | String | Business type, a business identifier such as order/contract/task, length 32 |
| bizId | biz_id | String | Business key, length 64 |
| actionUrl | action_url | String | Action URL, opened when the notification is clicked (frontend router.push or window.open), length 512 |
| channels | channels | String | Delivery channel snapshot JSON, e.g. `["inapp","email"]`, length 256 |
| priority | priority | int | Importance level: 0 normal, 1 reminder, 2 important, 3 urgent |

## NotificationUser

Recipient status of a notification, one row per recipient; separated from the notification subject so each recipient has independent read/starred/archived states and per-user inbox queries are supported. Extends `BaseEntity`.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| notificationId | notification_id | String | Notification id, non-null, length 32 |
| userId | user_id | String | Recipient id, non-null, length 64 |
| readStatus | read_status | int | Read status: 0 unread, 1 read |
| readAt | read_at | Date | Read time |
| starred | starred | int | Starred: 0 no, 1 yes |
| archived | archived | int | Archived: 0 no, 1 yes (archived by the user; hidden from the default inbox) |

## NotificationOutbox

The notification delivery outbox, one row per delivery channel. Transactional outbox: written inside the business transaction (ready), then asynchronously scanned, claimed (CAS → processing) and delivered by the scheduler through the corresponding DeliveryChannel; success → success, failure → exponential-backoff retry → dead letter (dead). A single channel's failure does not affect other channels. Extends `BaseEntity`.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| notificationId | notification_id | String | Notification id, non-null, length 32 |
| channel | channel | String | Delivery channel: `inapp` / `email` / `sms` / `wecom` etc., non-null, length 32 |
| recipientJson | recipient_json | String | Recipient userId list JSON, e.g. `["u1","u2"]`, non-null, length 2048 |
| status | status | String | Delivery status: `ready` / `processing` / `success` / `fail` / `dead`, length 16 |
| retryCount | retry_count | int | Retry count |
| nextRetryAt | next_retry_at | Date | Next retry time |
| errorMsg | error_msg | String | Error message, length 512 |

## UserEmailAccount

The user's external mailbox configuration: IMAP for receiving, SMTP for sending, OAuth2 and sync policy. Extends `BaseEntity`, implements `EntityEnableAble`.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| userId | user_id | String | User id, non-null |
| emailAddress | email_address | String | Email address, non-null |
| displayName | display_name | String | Display name |
| providerCode | provider_code | String | Mailbox provider: qq/163/126/gmail/outlook/other |
| defaultFlag | default_flag | int | Default mailbox: 0 no, 1 yes |
| imapHost | imap_host | String | IMAP host |
| imapPort | imap_port | Integer | IMAP port |
| imapSsl | imap_ssl | int | IMAP SSL: 0 no, 1 yes |
| imapFolderDefault | imap_folder_default | String | Default folder |
| authType | auth_type | String | Auth type: auth_code/password/oauth2 |
| authUser | auth_user | String | Auth username |
| authSecret | auth_secret | String | Auth secret (authorization code/password ciphertext) |
| oauth2Json | oauth2_json | String | OAuth2 info, JSON |
| smtpHost | smtp_host | String | SMTP host |
| smtpPort | smtp_port | Integer | SMTP port |
| smtpSsl | smtp_ssl | int | SMTP SSL: 0 no, 1 yes |
| smtpStarttls | smtp_starttls | int | SMTP STARTTLS: 0 no, 1 yes |
| smtpAuthUser | smtp_auth_user | String | SMTP auth username |
| smtpAuthSecret | smtp_auth_secret | String | SMTP auth secret (authorization code/password ciphertext) |
| smtpFromName | smtp_from_name | String | From display name |
| signatureHtml | signature_html | String | Email signature, HTML |
| enableStatus | enable_status | int | Enable status: 1 enabled, 0 disabled |
| syncEnabled | sync_enabled | int | Sync switch: 0 off, 1 on |
| syncIntervalMinutes | sync_interval_minutes | Integer | Sync interval (minutes), defaults to 5 |
| lastSyncAt | last_sync_at | Date | Last sync time |
| syncStatus | sync_status | String | Sync status: idle/syncing/error |

## UserStockMap

The user's frequent contacts relation. The property and column names keep the "stock" wording; the actual semantics are frequent contacts (address-book favorites). Extends `BaseEntity`; no catalog specified.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| userId | user_id | String | User id |
| stockId | stock_id | String | Contact id |
| appId | app_id | String | Application id |

## Recommended Reading

- [Platform Entity Index](platform-entity-index.md) — the Mail* entity list of the geelato-mail module
- [Business Alerting](../alert/overview.md) — alert messages delivered through the notification pipeline
