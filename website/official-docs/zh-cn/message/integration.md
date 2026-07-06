---
id: integration
title: 业务系统消息接入指南
sidebar_label: 消息入队接入
---

# Geelato Message 消息入队接入指南

本文档面向业务系统的开发者，说明如何调用 `geelato-message` 的消息入队接口，以及如何按短信、邮件、Bot、企业微信四类消息构造请求数据。

## 1. HTTP 接口入队 (推荐)

这是最常规、最推荐的业务系统接入方式。

**请求地址**：`POST /message/enqueue`  
**Content-Type**: `application/json`

### 1.1 接口响应结构
正常情况下，无论传入多少个接收人，按照对外协议约定，一次请求只入队一条消息体，返回该消息生成的 ID：
```json
{
  "ids": [
    "1938475629384756293"
  ]
}
```

### 1.2 核心请求参数说明

| 字段 | 是否必填 | 类型 | 说明 |
| --- | --- | --- | --- |
| `type` | **是** | `string` | 消息类型。如 `sms`, `email`, `bot`, `weixin_work_group`。 |
| `content` | **是** | `string` | 消息内容。邮件/Bot支持增强 JSON 格式。 |
| `receiver` | **是** | `string` | 接收人 JSON 字符串。格式见下方说明。 |
| `bizKey` | 建议 | `string` | 业务唯一键。建议由发送方自行构造并保持唯一（如 `workflow-approve-order123-email`）。 |
| `sender` | 建议 | `string` | 发送人标识。企业微信场景必须可识别。 |
| `buss` | 建议 | `string` | 业务线标识。 |
| `sourceSystem`| 建议 | `string` | 来源系统标识。未传时默认 `unknown`。 |
| `title` | 否 | `string` | 消息标题。邮件建议传。 |
| `planSendTime`| 否 | `datetime` | 计划发送时间。晚于当前时间则进入延时发送队列。 |

> **调试模式说明**：消息中心内部支持“调试模式”，可将消息强制转发给测试人员。该逻辑对调用方透明，调用方**始终按真实的接收人**构造 `receiver` 即可。

---

## 2. 接收人 (receiver) 构造规则

`receiver` 字段必须是一个 **JSON 字符串**，其内部基础结构如下：
```json
{
  "type": "mobilePhone",
  "list": ["13800138000"],
  "cc": ["copy@example.com"]
}
```

### 支持的 type 映射关系

| 接收人 `type` | 适用消息类型 | 说明 |
| --- | --- | --- |
| `mobilePhone` | `sms` | 直接传手机号列表 |
| `userId` | `sms`、`email` | 系统在发送阶段按用户 ID 解析手机号或邮箱 |
| `emailAddress` | `email` | 直接传邮箱地址列表 |
| `weixinWorkUserId` | `weixin_work_group` | 直接传企业微信用户 ID |
| `weixinWorkGroupId`| `weixin_work_group` | 直接传企业微信群 ID |

*(注：Bot 场景不使用上述结构，见下文示例)*

---

## 3. 各渠道报文构造示例

### 3.1 短信 (SMS)
```bash
curl -X POST "http://localhost:8080/message/enqueue" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "验证码短信",
    "content": "您的验证码是 123456。",
    "sender": "user_1001",
    "buss": "login",
    "type": "sms",
    "bizKey": "login-1001-sms",
    "sourceSystem": "passport",
    "receiver": "{\"type\":\"mobilePhone\",\"list\":[\"13800138000\"]}"
  }'
```

### 3.2 邮件 (Email)
邮件的 `content` 支持纯 HTML，也支持增强型 JSON（用于携带附件）。
```bash
curl -X POST "http://localhost:8080/message/enqueue" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "系统通知",
    "content": "{\"text\":\"<p>请查收今日日报。</p>\",\"contentType\":\"html\",\"attachments\":[{\"name\":\"report.xlsx\",\"url\":\"https://example.com/report.xlsx\"}]}",
    "sender": "user_1001",
    "buss": "notice",
    "type": "email",
    "bizKey": "notice-20260702-email",
    "sourceSystem": "oa",
    "receiver": "{\"type\":\"emailAddress\",\"list\":[\"a@example.com\"],\"cc\":[\"copy@example.com\"]}"
  }'
```

### 3.3 Bot 群聊机器人
Bot 场景的 `receiver` 和 `content` 都有特殊的 JSON 结构要求：
```bash
curl -X POST "http://localhost:8080/message/enqueue" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Bot群消息",
    "content": "{\"content_type\":\"text\",\"text\":\"你好，这是一条测试消息\"}",
    "sender": "demo_sender_001",
    "buss": "bot-notice",
    "type": "bot",
    "bizKey": "bot-msg-20260702-001",
    "sourceSystem": "crm",
    "receiver": "{\"session_type\":\"group\",\"session\":\"CP20260000X-测试客户\"}"
  }'
```

### 3.4 企业微信应用消息
企业微信消息能否成功发送，关键在于**发送人(`sender`)信息必须完整且可识别**，因为底层 RouteHandler 需要通过 sender 来反查对应的公司企微配置。
```bash
curl -X POST "http://localhost:8080/message/enqueue" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "企业微信通知",
    "content": "请及时处理审批任务。",
    "sender": "demo_sender_001",
    "buss": "workflow",
    "type": "weixin_work_group",
    "bizKey": "workflow-approve-001",
    "sourceSystem": "workflow",
    "receiver": "{\"type\":\"weixinWorkUserId\",\"list\":[\"zhangsan\",\"lisi\"]}"
  }'
```

---

## 4. 数据库直接入队

对于批量补数或离线脚本，可以直接向 `platform_msg` 表中插入数据。

**注意：**
1. 主键 `id` 必须使用雪花算法生成。
2. 数据库入队**不会**自动补齐 `route` 字段，必须根据 `type` 自行硬编码写入对应的 RouteHandler Name（如 `tenantSmsRouteHandler`）。
3. 数据库不会做入参校验，写入前请确保 `receiver` 和 `content` 的 JSON 格式完全正确。

**SQL 模板示例 (短信)：**
```sql
INSERT INTO platform_msg
(id, title, content, sender, receiver, type, status, channel, route, tenant_code,
 idempotency_key, biz_key, priority, source_system, del_status, update_at, updater,
 create_at, creator, buss, plan_send_time, retry_count, max_retry_count, archive_status,
 trace_id, channel_detail, receiver_snapshot)
VALUES
('331286567354241051', '测试消息', '消息内容', 'sender_001',
 '{"type":"mobilePhone","list":["13800138000"]}', 'sms', 'ready', 'default',
 'tenantSmsRouteHandler', 'demo_tenant', 'biz_demo_001', 'biz_demo_001', 0, 'demo-script', 0,
 NOW(), 'system', NOW(), 'system', 'demo', NOW(), 0, 5, 'hot',
 'trace_demo_001', 'route=tenantSmsRouteHandler',
 '{"type":"mobilePhone","list":["13800138000"]}');
```