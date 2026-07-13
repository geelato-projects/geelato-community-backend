---
id: overview
title: Message Center Overview
sidebar_label: Message Center Overview
---

# Independent Service: Unified Message Center (geelato-message)

## 1. Positioning and Boundaries

Similar to the unified authentication service, `geelato-message` **is essentially an independent message center service**, rather than a mandatory framework underlying capability.

It mainly provides the following core capabilities:
1. **Unified Message Sending Gateway**: Consolidates sending requests from the entire platform and various business systems.
2. **Multi-channel Distribution**: Built-in support for multiple sending channels such as SMS, Email, WeCom Bot, and WeCom Application Messages.
3. **Tenant & Route-based Configuration Isolation**: The core framework of the message center only understands "Tenant Domain" and "Route". Complex distribution logic such as company and business lines is carried by the underlying `RouteHandler`.

**This document mainly explains**: How external business systems or internal platform modules can integrate message push requests into this unified message center.

## 2. Core Concepts and Routing Architecture

When the message center processes a message, there are two core fields:

- **`channel` (Content Processing Pipeline)**: Responsible for message content preprocessing (e.g., template rendering, sensitive word filtering, etc.). Currently, it defaults to the `default` pipeline.
- **`route` (Send Routing Processor)**: Responsible for deciding which configuration this message will ultimately use to be sent out. This is the core design of the message center.

### 2.1 Route Distribution Mechanism

The message center does not use a massive and complex general configuration domain (no concept of scope is introduced), but adopts a minimalist **"Tenant Domain + Routing Processor"** model:
1. When upstream requests are enqueued, there is **no need to pass** the `route` parameter.
2. The message center enqueue service (`RouteAssignService`) automatically fills in the `route` based on the message type (`type`) (e.g., `sms` -> `tenantSmsRouteHandler`).
3. The corresponding `RouteHandler` takes over the message and reads the entire message content (including `tenantCode`, `sender`, etc.).
4. The `RouteHandler` internally decides how to hit the final sending configuration from the database (or external interfaces).

This design keeps the core framework very clean, while complex business distribution logic (e.g., parsing the corresponding WeCom configuration based on the `sender`) is completely submerged and isolated within specific RouteHandlers.

## 3. Supported Message Types

Currently, the message center natively supports the enqueue and sending of the following message types:

| Message Type (`type`) | Receiver Type (`receiver.type`) | Applicable Scenarios | Corresponding Built-in Route |
| :--- | :--- | :--- | :--- |
| `sms` | `mobilePhone`, `userId` | Mobile SMS | `tenantSmsRouteHandler` |
| `email` | `emailAddress`, `userId` | Email (Supports HTML and attachments) | `tenantEmailRouteHandler` |
| `bot` | Dedicated `session` structure | Group chat bot notifications (e.g., WeCom) | `tenantBotRouteHandler` |
| `weixin_work_group`| `weixinWorkUserId`, `weixinWorkGroupId` | WeCom Application/Group messages | `companyWeworkRouteHandler` |

## 4. Integration Methods

The message center supports two enqueue methods. Please choose according to your actual scenario:

1. **HTTP API Enqueue (Recommended for routine business)**:
   Business systems call the `/message/enqueue` API, sending a lightweight JSON. The message center automatically completes the remaining internal fields (such as Primary Key ID, Status, Route, etc.).
   
2. **Database Direct Enqueue (Recommended for offline/batch scripts)**:
   Insert data directly into the `platform_msg` table. This method requires the business side to generate Snowflake IDs themselves and complete all required business fields.

For detailed integration message formats and sequence, please read the next section [《Business System Message Integration Guide》](./integration.md).