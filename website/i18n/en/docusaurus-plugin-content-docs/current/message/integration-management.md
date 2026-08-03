---
id: integration-management
title: Integration Management
sidebar_label: Integration Management
---

# Integration Management (Push / Pull)

Integration Management is the capability provided by the message center to bridge external systems with the message center. It is divided into two categories by data flow direction:

- **Push integration**: An external system actively calls the message center to enqueue messages (see [Message Enqueue Integration](./integration.md)). Integration Management is responsible for **registering the external system**, and can optionally verify the caller's identity against the Auth Server.
- **Pull integration**: The message center periodically pulls data from an external data source (JDBC) according to configuration. Each pulled row is automatically turned into a message and enqueued, going through the same unified dispatch pipeline as push.

> Prerequisite: It is recommended to first understand the routing and message type concepts in [Message Center Overview](./overview.md).

## 1. Capability Overview

| Dimension | Push Integration | Pull Integration |
| :--- | :--- | :--- |
| Data flow | External system → message center | External data source → message center |
| Trigger | External business system actively calls | Message center scheduled task |
| Registered object | External system (systemCode / clientId) | JDBC pull source (connection string + SQL + templates) |
| Credential source | Reuses Auth Server `oauth_client`; no secret stored locally | DB password stored locally (AES-GCM ciphertext + UI masking) |
| How it lands | Calls `/message/enqueue` into `platform_msg` | Pulled rows rendered and inserted into `platform_msg` |
| Authentication | Optional, verifies token against Auth Server | Not applicable (center calls out) |

Both are maintained under the **Integration Management** menu in the ops console.

## 2. Push Integration: External System Registration

### 2.1 Relationship with the Auth Server

The `clientId` / `systemCode` registered for push integration **reuse the `oauth_client` already registered in the Auth Server** (geelato-auth). The message center **does not store the client secret** again.

- `systemCode`: Unique code of the external system, corresponding to `oauth_client.system_code` in the Auth Server.
- `clientId`: Client ID issued by the Auth Server. In the create/edit dialog you can pick from the Auth Server's clients (requires `geelato.message.integration.auth-base-url` to be configured).
- When enqueue verification is enabled, the message center calls the Auth Server to verify the access_token carried by the caller. On success, the message's `sourceSystem` is back-filled with the registered system's `systemCode`.

### 2.2 Registration Fields (`platform_msg_integration_system`)

| Field | Required | Description |
| :--- | :--- | :--- |
| `systemCode` | **Yes** | System code (unique), corresponds to Auth Server `oauth_client.system_code`. |
| `systemName` | **Yes** | System name, for easy identification. |
| `clientId` | No | Auth Server clientId (referenced, no secret stored). |
| `authType` | No | Auth method: `oauth_token` / `api_key` / `none`, default `none`. |
| `authVerifyEnabled` | No | Whether to verify token on enqueue: `1` verify / `0` skip, default `0`. |
| `contact` | No | Contact/owner. |
| `enableStatus` | No | Enabled status: `1` enabled / `0` disabled. |
| `remark` | No | Remark. |

### 2.3 Optional Enqueue Verification

By default `/message/enqueue` does not verify the caller identity (backward compatible). To verify against the Auth Server:

1. Configure the Auth Server URL: `geelato.message.integration.auth-base-url=http://<auth-server>`
2. Enable verification: `geelato.message.integration.auth-verify-enabled=true`
3. The caller must carry these headers on enqueue:
   - `X-Client-Id: <clientId>`
   - `Authorization: Bearer <accessToken>`

On success, the message's `sourceSystem` is back-filled with the `systemCode` of the registered system matching that `clientId`; on failure, `401` is returned.

```bash
curl -X POST "http://localhost:8086/message/enqueue" \
  -H "Content-Type: application/json" \
  -H "X-Client-Id: order-center" \
  -H "Authorization: Bearer <accessToken>" \
  -d '{
    "type": "sms",
    "content": "Your order has been shipped.",
    "bizKey": "order-1001-sms",
    "receiver": "{\"type\":\"mobilePhone\",\"list\":[\"13800138000\"]}"
  }'
```

> When verification headers are absent or the master switch is off, enqueue proceeds as before, without affecting existing callers.

## 3. Pull Integration: JDBC Pull Source

### 3.1 How It Works

1. **Unified scheduling**: The message center scans all **enabled** pull sources at a single global interval (`geelato.message.integration.pull.interval-seconds`, default 30 seconds).
2. **Execute pull**: For each source, a short-lived connection is opened using its JDBC connection string, and `pullSql` is executed (in incremental mode the last watermark `:last_pull_at` is bound).
3. **Template rendering**: For each row in the result set, the `target_*` templates render the title, content, and receiver.
4. **Enqueue & dispatch**: The rendered message goes through the same enqueue logic as push into `platform_msg`, reusing the idempotency key (`pull:<sourceCode>:<primary key>`) for deduplication, plus routing, retry, and archiving.
5. **Record watermark**: The source's `last_pull_at` / `last_pull_status` / `last_pull_count` are updated for ops visibility and incremental use.

> The pull frequency is managed centrally by the message center. An individual pull source can only be enabled or disabled; it cannot define its own frequency.

### 3.2 Pull Source Fields (`platform_msg_pull_source`)

| Field | Required | Description |
| :--- | :--- | :--- |
| `sourceCode` | **Yes** | Pull source code (unique). |
| `sourceName` | **Yes** | Pull source name. |
| `jdbcUrl` | **Yes** | JDBC connection string of the external data source. |
| `dbUsername` | No | DB username. |
| `dbPassword` | No | DB password (entered in the UI; stored as AES-GCM ciphertext; masked on display). |
| `dbDriver` | No | JDBC driver class, default `com.mysql.cj.jdbc.Driver`. |
| `pullSql` | **Yes** | Pull SQL, supports the `:last_pull_at` placeholder (incremental). |
| `incrementalFlag` | No | Incremental flag: `1` incremental / `0` full, default `0`. |
| `targetMsgType` | **Yes** | Message type the row is turned into: `sms` / `email` / `bot`, etc. |
| `targetReceiver` | No | Target receiver JSON, supports `${col}` placeholders. |
| `targetBuss` | No | Business identifier. |
| `targetTitleTemplate` | No | Title template, supports `${col}` placeholders. |
| `targetContentTemplate` | No | Content template, supports `${col}` placeholders; when blank the whole row JSON is used as content. |
| `enableStatus` | No | Enabled status: `1` enabled / `0` disabled. |
| `lastPullStatus` | — | Last pull status: `idle` / `running` / `success` / `fail` (system-maintained). |
| `lastPullAt` | — | Last pull watermark (system-maintained, for incremental). |
| `lastPullCount` | — | Row count of the last pull (system-maintained). |

### 3.3 Template & Placeholder Syntax

- **Row field placeholders**: Use `${columnName}` in `targetTitleTemplate` / `targetContentTemplate` / `targetReceiver`. It is replaced with the current row's column value at pull time. For example, if the SQL returns columns `order_no` and `mobile`, the template can be `Order ${order_no} shipped, contact ${mobile}`.
- **Incremental watermark placeholder**: Use `:last_pull_at` in `pullSql`. When incremental mode is on, the last pull time is bound automatically, e.g.:
  ```sql
  SELECT id, order_no, mobile FROM orders WHERE update_at > :last_pull_at
  ```
- **Idempotent deduplication**: Each row is enqueued with the business key `pull:<sourceCode>:<primary-key-column-value>`. The primary key column is identified in the order `id` / `*_id` / `*_no` / `*_code` / `biz_key`. In full mode, repeated pulls do not produce duplicate messages.

### 3.4 Complete Example

**Scenario**: Pull orders to be notified from the order database every 30 seconds and send an SMS to customers.

Pull source configuration:

| Config | Value |
| :--- | :--- |
| sourceCode | `order-alert` |
| jdbcUrl | `jdbc:mysql://10.0.0.5:3306/orders?useSSL=false` |
| incrementalFlag | `1` |
| pullSql | `SELECT id, order_no, mobile FROM orders WHERE update_at > :last_pull_at AND notify_flag = 0` |
| targetMsgType | `sms` |
| targetReceiver | `{"type":"mobilePhone","list":["${mobile}"]}` |
| targetTitleTemplate | `Order ${order_no} Notice` |
| targetContentTemplate | `Your order ${order_no} status has been updated. Please check it in time.` |
| targetBuss | `order` |

After pulling a row `{id: 8821, order_no: "SO2026080101", mobile: "13800138000"}`, the rendered and enqueued message is equivalent to:

```json
{
  "type": "sms",
  "title": "Order SO2026080101 Notice",
  "content": "Your order SO2026080101 status has been updated. Please check it in time.",
  "bizKey": "pull:order-alert:8821",
  "sourceSystem": "pull:order-alert",
  "buss": "order",
  "receiver": "{\"type\":\"mobilePhone\",\"list\":[\"13800138000\"]}"
}
```

This message then follows the exact same routing and sending pipeline as a pushed message.

## 4. Configuration

Configured in `application.properties` (all have defaults and work out of the box):

| Property | Default | Description |
| :--- | :--- | :--- |
| `geelato.message.integration.enabled` | `true` | Master switch for integration management. When off, scheduling and verification are disabled (registration/query still work). |
| `geelato.message.integration.pull.enabled` | `true` | Pull scheduling switch. |
| `geelato.message.integration.pull.interval-seconds` | `30` | Global pull interval, in seconds. |
| `geelato.message.integration.pull.max-rows` | `1000` | Maximum rows per pull (0 means unlimited). |
| `geelato.message.integration.auth-base-url` | (empty) | Auth Server URL; once set, oauth client passthrough and enqueue token verification are enabled. |
| `geelato.message.integration.auth-verify-enabled` | `false` | Whether to verify access_token on enqueue. |
| `geelato.message.integration.crypto-key` | built-in default | Credential encryption key. **In production, always override it via the environment variable `GEELATO_MESSAGE_INTEGRATION_CRYPTO_KEY`.** |

## 5. REST API Reference

All integration management APIs share the prefix `/message/ops/integration` and return the unified envelope `{code, msg, data, total, page, size}`.

### 5.1 Push Systems

| Method | Path | Description |
| :--- | :--- | :--- |
| `GET` | `/systems` | Page query for push systems (params `keyword` / `authType` / `enableStatus` / `page` / `size`). |
| `GET` | `/systems/{id}` | Push system detail. |
| `POST` | `/systems` | Create a push system. |
| `PUT` | `/systems/{id}` | Update a push system. |
| `POST` | `/systems/{id}/enable` | Enable. |
| `POST` | `/systems/{id}/disable` | Disable. |
| `DELETE` | `/systems/{id}` | Delete (logical). |
| `GET` | `/auth-clients` | Passthrough of Auth Server oauth client list, for selecting a clientId. |

### 5.2 Pull Sources

| Method | Path | Description |
| :--- | :--- | :--- |
| `GET` | `/pull-sources` | Page query for pull sources (params `keyword` / `enableStatus` / `lastPullStatus` / `page` / `size`). |
| `GET` | `/pull-sources/{id}` | Pull source detail (password masked). |
| `POST` | `/pull-sources` | Create a pull source. |
| `PUT` | `/pull-sources/{id}` | Update a pull source (leave password blank to keep unchanged). |
| `POST` | `/pull-sources/{id}/enable` | Enable. |
| `POST` | `/pull-sources/{id}/disable` | Disable. |
| `DELETE` | `/pull-sources/{id}` | Delete (logical). |
| `POST` | `/pull-sources/{id}/test?sample=3` | Pull once immediately (no persistence); returns row count and first 3 sample rows. |
| `POST` | `/pull-sources/{id}/run` | Execute one pull immediately and persist messages. |

## 6. Ops UI Guide

Open the **Integration Management** menu in the ops console. The page is organized into two tabs:

- **Push Systems**: The list supports search by code/name/clientId; the create/edit dialog pulls the `clientId` dropdown from the Auth Server; you can enable/disable/delete. Toggling "Enqueue Verification" on means callers of that system must carry a token.
- **Pull Sources**: The list shows code, name, JDBC, last pull status/count/time; the create/edit dialog configures connection info, pull SQL, and target message templates; the password field is left blank to keep unchanged when editing. Row actions:
  - **Test**: Pull once immediately; a dialog shows the row count and sample data for debugging SQL and templates, **without producing messages**.
  - **Run**: Pull once immediately and persist messages, for ad-hoc triggers or verification.

> Before enabling, run the schema script `platform_msg_integration.sql` (located in the `geelato-message` module root).
