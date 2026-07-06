---
id: integration
title: Business System Message Integration Guide
sidebar_label: Message Enqueue Integration
---

# Geelato Message Enqueue Integration Guide

This document is intended for developers of business systems, explaining how to call the message enqueue API of `geelato-message`, and how to construct request data for four types of messages: SMS, Email, Bot, and WeCom (Weixin Work).

## 1. HTTP API Enqueue (Recommended)

This is the most conventional and recommended integration method for business systems.

**Request URL**: `POST /message/enqueue`  
**Content-Type**: `application/json`

### 1.1 API Response Structure
Under normal circumstances, regardless of how many receivers are passed in, according to the external protocol agreement, one request only enqueues one message body and returns the generated ID of that message:
```json
{
  "ids": [
    "1938475629384756293"
  ]
}
```

### 1.2 Core Request Parameter Description

| Field | Required | Type | Description |
| --- | --- | --- | --- |
| `type` | **Yes** | `string` | Message type. e.g., `sms`, `email`, `bot`, `weixin_work_group`. |
| `content` | **Yes** | `string` | Message content. Email/Bot supports enhanced JSON format. |
| `receiver` | **Yes** | `string` | Receiver JSON string. See format below. |
| `bizKey` | Recommended | `string` | Business unique key. Recommended to be constructed by the sender and kept unique (e.g., `workflow-approve-order123-email`). |
| `sender` | Recommended | `string` | Sender identifier. Must be recognizable in WeCom scenarios. |
| `buss` | Recommended | `string` | Business line identifier. |
| `sourceSystem`| Recommended | `string` | Source system identifier. Defaults to `unknown` if not passed. |
| `title` | No | `string` | Message title. Recommended for Email. |
| `planSendTime`| No | `datetime` | Planned send time. If later than the current time, it enters the delayed sending queue. |

> **Debug Mode Note**: The message center internally supports a "debug mode", which can forcibly forward messages to testers. This logic is transparent to the caller. The caller should **always construct the `receiver` according to the real receiver**.

---

## 2. Receiver Construction Rules

The `receiver` field must be a **JSON string**. Its basic internal structure is as follows:
```json
{
  "type": "mobilePhone",
  "list": ["13800138000"],
  "cc": ["copy@example.com"]
}
```

### Supported Type Mappings

| Receiver `type` | Applicable Message Type | Description |
| --- | --- | --- |
| `mobilePhone` | `sms` | Pass mobile phone number list directly |
| `userId` | `sms`, `email` | System parses mobile phone or email based on User ID during sending phase |
| `emailAddress` | `email` | Pass email address list directly |
| `weixinWorkUserId` | `weixin_work_group` | Pass WeCom User ID directly |
| `weixinWorkGroupId`| `weixin_work_group` | Pass WeCom Group ID directly |

*(Note: Bot scenarios do not use the above structure, see examples below)*

---

## 3. Message Construction Examples for Each Channel

### 3.1 SMS
```bash
curl -X POST "http://localhost:8080/message/enqueue" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Verification Code SMS",
    "content": "Your verification code is 123456.",
    "sender": "user_1001",
    "buss": "login",
    "type": "sms",
    "bizKey": "login-1001-sms",
    "sourceSystem": "passport",
    "receiver": "{\"type\":\"mobilePhone\",\"list\":[\"13800138000\"]}"
  }'
```

### 3.2 Email
The `content` of an email supports plain HTML, as well as enhanced JSON (used to carry attachments).
```bash
curl -X POST "http://localhost:8080/message/enqueue" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "System Notification",
    "content": "{\"text\":\"<p>Please check today's report.</p>\",\"contentType\":\"html\",\"attachments\":[{\"name\":\"report.xlsx\",\"url\":\"https://example.com/report.xlsx\"}]}",
    "sender": "user_1001",
    "buss": "notice",
    "type": "email",
    "bizKey": "notice-20260702-email",
    "sourceSystem": "oa",
    "receiver": "{\"type\":\"emailAddress\",\"list\":[\"a@example.com\"],\"cc\":[\"copy@example.com\"]}"
  }'
```

### 3.3 Bot Group Chat Robot
Both `receiver` and `content` in Bot scenarios have special JSON structure requirements:
```bash
curl -X POST "http://localhost:8080/message/enqueue" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Bot Group Message",
    "content": "{\"content_type\":\"text\",\"text\":\"Hello, this is a test message\"}",
    "sender": "demo_sender_001",
    "buss": "bot-notice",
    "type": "bot",
    "bizKey": "bot-msg-20260702-001",
    "sourceSystem": "crm",
    "receiver": "{\"session_type\":\"group\",\"session\":\"CP20260000X-TestCustomer\"}"
  }'
```

### 3.4 WeCom Application Message
The key to whether a WeCom message can be sent successfully is that **the sender (`sender`) information must be complete and recognizable**, because the underlying RouteHandler needs to use the sender to reverse-query the corresponding company WeCom configuration.
```bash
curl -X POST "http://localhost:8080/message/enqueue" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "WeCom Notification",
    "content": "Please process the approval task promptly.",
    "sender": "demo_sender_001",
    "buss": "workflow",
    "type": "weixin_work_group",
    "bizKey": "workflow-approve-001",
    "sourceSystem": "workflow",
    "receiver": "{\"type\":\"weixinWorkUserId\",\"list\":[\"zhangsan\",\"lisi\"]}"
  }'
```

---

## 4. Database Direct Enqueue

For batch data filling or offline scripts, you can insert data directly into the `platform_msg` table.

**Note:**
1. The primary key `id` must be generated using the Snowflake algorithm.
2. Database enqueue **will not** automatically fill in the `route` field. You must hardcode the corresponding RouteHandler Name (e.g., `tenantSmsRouteHandler`) based on the `type`.
3. The database will not validate input parameters. Please ensure the JSON formats of `receiver` and `content` are completely correct before writing.

**SQL Template Example (SMS):**
```sql
INSERT INTO platform_msg
(id, title, content, sender, receiver, type, status, channel, route, tenant_code,
 idempotency_key, biz_key, priority, source_system, del_status, update_at, updater,
 create_at, creator, buss, plan_send_time, retry_count, max_retry_count, archive_status,
 trace_id, channel_detail, receiver_snapshot)
VALUES
('331286567354241051', 'Test Message', 'Message Content', 'sender_001',
 '{"type":"mobilePhone","list":["13800138000"]}', 'sms', 'ready', 'default',
 'tenantSmsRouteHandler', 'demo_tenant', 'biz_demo_001', 'biz_demo_001', 0, 'demo-script', 0,
 NOW(), 'system', NOW(), 'system', 'demo', NOW(), 0, 5, 'hot',
 'trace_demo_001', 'route=tenantSmsRouteHandler',
 '{"type":"mobilePhone","list":["13800138000"]}');
```