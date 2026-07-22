---
title: 消息中心总览
sidebar_label: 消息中心总览
---

# 独立服务：统一消息中心 (geelato-message)

## 1. 定位与边界

与统一认证服务类似，`geelato-message` **本质上是一个独立的消息中心服务**，而不是必须强绑定的框架底层能力。

它主要提供以下核心能力：
1. **统一的消息发送网关**：收口全平台及各业务系统的发送请求。
2. **多渠道分发能力**：内置支持 短信、邮件、企微Bot、企业微信应用消息 等多种发送渠道。
3. **基于租户与路由的配置隔离**：消息中心核心框架只理解“租户域”与“路由(Route)”，具体复杂的公司、业务线分发逻辑由底层 RouteHandler 承载。

**本文档主要说明**：外部业务系统或平台内部模块，如何将消息推送请求接入到这个统一消息中心。

## 2. 核心概念与路由架构

消息中心在处理消息时，有两个核心字段：

- **`channel` (内容处理管道)**：负责消息内容的预处理（例如模板渲染、敏感词过滤等），目前默认走 `default` 管道。
- **`route` (发送路由处理器)**：负责决定这条消息最终走哪套配置发出去。这是消息中心的核心设计。

### 2.1 Route 分发机制

消息中心不采用庞大复杂的通用配置域（不引入 scope 等概念），而是采用了极简的 **“租户域 + 路由处理器”** 模式：
1. 上游请求入队时，**无需传递** `route` 参数。
2. 消息中心入队服务 (`RouteAssignService`) 根据消息类型(`type`)自动补齐 `route`（例如 `sms` -> `tenantSmsRouteHandler`）。
3. 对应的 `RouteHandler` 接管消息，读取整条消息内容（包括 `tenantCode`、`sender` 等）。
4. 由 `RouteHandler` 内部决定如何从数据库（或外部接口）命中最终的发送配置。

这种设计使得核心框架非常干净，而复杂的业务分发逻辑（例如按发送人 `sender` 解析出对应的企业微信配置）完全被下沉隔离在具体的 RouteHandler 中。

## 3. 支持的消息类型

目前消息中心原生支持以下消息类型的入队与发送：

| 消息类型 (`type`) | 接收人类型 (`receiver.type`) | 适用场景 | 对应的内置 Route |
| :--- | :--- | :--- | :--- |
| `sms` | `mobilePhone`, `userId` | 手机短信 | `tenantSmsRouteHandler` |
| `email` | `emailAddress`, `userId` | 邮件（支持HTML及附件） | `tenantEmailRouteHandler` |
| `bot` | 专用 `session` 结构 | 企微等群聊机器人通知 | `tenantBotRouteHandler` |
| `weixin_work_group`| `weixinWorkUserId`, `weixinWorkGroupId` | 企业微信应用/群消息 | `companyWeworkRouteHandler` |

## 4. 接入方式

消息中心支持两种入队方式，请根据实际场景选择：

1. **HTTP 接口入队（推荐常规业务使用）**：
   业务系统调用 `/message/enqueue` 接口，发送轻量级的 JSON，由消息中心自动补全其余内部字段（如主键ID、状态、路由等）。
   
2. **数据库直接入队（推荐离线/批量脚本使用）**：
   直接往 `platform_msg` 表中插入数据。此方式需要业务方自行生成雪花 ID 并补全所有必填业务字段。

详细的接入报文格式与时序，请阅读下一节 [《业务系统消息接入指南》](./integration.md)。