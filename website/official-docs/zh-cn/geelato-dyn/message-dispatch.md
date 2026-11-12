---
title: 统一消息分发
sidebar_label: 统一消息分发
---

# 统一消息分发

`geelato-dyn-common` 内置统一消息分发能力并注册为 OSGi 服务 `MessageDispatcher`。设计原则：

- **业务模块零渠道配置**：只组装 `MessageRequest` 并调用 `dispatch`，不感知任何渠道地址与令牌；
- **不直连平台库**：email / sms / bot / 企微渠道为"出箱式"，经 REST 提交 geelato-message 消息中心（`POST /message/enqueue`），由消息中心负责幂等、路由、`platform_msg` 落库与实际分发；
- **渠道配置集中一处**：全部装配在 `geelato-dyn-common` 的 `geelato-common-message.xml`，改一处所有业务模块同时生效。

关于消息中心本身的报文格式与路由机制，见[消息中心文档](../message/overview.md)。

## 接入方式（业务模块）

```xml
<!-- Blueprint：零配置引用 -->
<reference id="messageDispatcher" interface="cn.geelato.dyn.common.message.MessageDispatcher"/>
```

```java
MessageRequest request = new MessageRequest();
request.setTitle("库存预警");
request.setTextContent("SKU A1001 低于安全库存");
request.setChannels(List.of(MessageChannel.EMAIL, MessageChannel.INAPP));
request.setRecipients(...);                    // MessageRecipients
request.setBizKeyBase("stock-warning");        // 幂等键前缀
request.setTenantCode("geelato");
List<String> msgIds = messageDispatcher.dispatch(request);   // 返回 platform_msg id
```

类型在 `cn.geelato.dyn.common.message.model` 包：`MessageRequest`（setter 风格 POJO）、`MessageRecipients`、`MessageAttachment`、`MessageChannel`（渠道码常量）。

## MessageRequest 模型

| 字段 | 说明 |
|---|---|
| `title` / `textContent` / `htmlContent` | 标题与正文：纯文本提醒只填 `textContent`；富文本邮件填 `htmlContent`（站内信无 htmlContent 时回退文本） |
| `attachments` | 附件列表（邮件作附件；企微可据第一个附件构造链接卡片） |
| `linkUrl` / `linkTitle` / `linkDesc` | 企微 link 卡片（`linkUrl` 为空时适配器回退取第一个附件 url） |
| `recipients` | `MessageRecipients` 收件人集合 |
| `channels` | 渠道码列表，常量见 `MessageChannel` |
| `bizKeyBase` | 业务幂等键前缀，最终 `platform_msg.biz_key = bizKeyBase + ":" + channelCode` |
| `tenantCode` / `sender` | 租户（默认 geelato）与发送者标识 |
| 其余 | 渠道扩展属性等，见源码 Javadoc |

## 渠道与适配器

SPI：`cn.geelato.dyn.common.message.channel.MessageChannelAdapter`（`channelCode()` + `resolve(MessageRequest)`），基类 `AbstractMessageChannelAdapter`，注册表 `MessageChannelRegistry` 分两组管理：

| 渠道 | 链路 | 说明 |
|---|---|---|
| `email` | 出箱（消息中心） | 支持 HTML 与附件 |
| `sms` | 出箱（消息中心） | 短信 |
| `bot` | 出箱（消息中心） | 企微群机器人 |
| `weixin_work_group` | 出箱（消息中心） | 企业微信应用/群消息 |
| `weixin_work_robot` | 出箱（消息中心） | **暂不可用**：消息中心尚无该 type 的路由映射，入队会被服务端拒绝，待补充路由后启用 |
| `inapp` | **直连**（不经消息中心） | `InAppDirectChannelAdapter` 同步 POST 社区平台站内信（`Authorization: SystemToken` 固定密钥）；`enabled=false` 时该渠道被跳过，不回退出箱 |

## 渠道配置

出箱与直连的配置集中在 `geelato-dyn-common/src/main/resources/OSGI-INF/blueprint/geelato-common-message.xml`（部署时按环境调整）：

```xml
<!-- 消息中心出箱器：systemCode 写 X-System-Code 头，apiKey 写 Authorization: Bearer 头
     （仅当消息中心启用集成鉴权时填写） -->
<bean id="messageCenterOutbox" class="...channel.MessageCenterOutbox">
    <property name="baseUrl" value="<消息中心地址>"/>
    <property name="systemCode" value="geelato-dyn"/>
    <property name="apiKey" value="<apiKey>"/>
    <property name="timeoutMs" value="10000"/>
</bean>

<!-- 站内信直连：communityToken 填社区平台 SystemToken 固定密钥，
     需与社区 geelato.security.system-token.token 一致（不能用 Bearer） -->
<bean id="inAppDirectChannelAdapter" class="...channel.InAppDirectChannelAdapter">
    <property name="enabled" value="true"/>
    <property name="baseUrl" value="<社区平台地址>"/>
    <property name="communityToken" value="<SystemToken>"/>
    <property name="timeoutMs" value="10000"/>
</bean>
```

:::note 改配置的方式
出箱地址、直连地址、令牌都在这份 XML 的 bean 属性上，属[Blueprint 内 bean 配置](configuration.md#blueprint-内-bean-配置)：修改后需重新构建发布 common bundle 并重装 feature，所有业务模块同时生效。
:::

## 扩展新渠道

实现 `MessageChannelAdapter`（或继承 `AbstractMessageChannelAdapter`），把新适配器加入 `MessageChannelRegistry` 的 adapters / directChannels 列表并重新发布 common；业务模块通过 `MessageChannel` 常量即可选用。

## 推荐继续阅读

- [内置能力](capabilities.md) —— 用户目录（收件人批量解析）等配套能力
- [统一回调与消费总线](callback-bus.md) —— 反向链路：接收第三方回调
