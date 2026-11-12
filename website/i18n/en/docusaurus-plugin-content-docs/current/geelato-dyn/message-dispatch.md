---
title: Unified Message Dispatch
sidebar_label: Message Dispatch
---

# Unified Message Dispatch

`geelato-dyn-common` ships a unified message dispatch capability registered as the OSGi service `MessageDispatcher`. Design principles:

- **Zero channel configuration for business modules**: they assemble a `MessageRequest` and call `dispatch`, unaware of any channel address or token;
- **No direct platform-database access**: the email / sms / bot / WeCom channels are "outbox style" — they submit over REST to the geelato-message center (`POST /message/enqueue`), which handles idempotency, routing, `platform_msg` persistence and actual delivery;
- **Channel configuration lives in one place**: everything is wired in `geelato-dyn-common`'s `geelato-common-message.xml`; one change affects all business modules.

For the message center's own payload format and routing, see the [Message Center docs](../message/overview.md).

## Integration (business modules)

```xml
<!-- Blueprint: zero-config reference -->
<reference id="messageDispatcher" interface="cn.geelato.dyn.common.message.MessageDispatcher"/>
```

```java
MessageRequest request = new MessageRequest();
request.setTitle("Stock warning");
request.setTextContent("SKU A1001 below safety stock");
request.setChannels(List.of(MessageChannel.EMAIL, MessageChannel.INAPP));
request.setRecipients(...);                    // MessageRecipients
request.setBizKeyBase("stock-warning");        // idempotency key prefix
request.setTenantCode("geelato");
List<String> msgIds = messageDispatcher.dispatch(request);   // returns platform_msg ids
```

Types live in the `cn.geelato.dyn.common.message.model` package: `MessageRequest` (setter-style POJO), `MessageRecipients`, `MessageAttachment`, `MessageChannel` (channel code constants).

## The MessageRequest Model

| Field | Notes |
|---|---|
| `title` / `textContent` / `htmlContent` | Title and body: plain-text reminders only need `textContent`; rich emails fill `htmlContent` (in-app falls back to text when htmlContent is absent) |
| `attachments` | Attachment list (email attachments; WeCom may build a link card from the first attachment) |
| `linkUrl` / `linkTitle` / `linkDesc` | WeCom link card (when `linkUrl` is empty the adapter may fall back to the first attachment url) |
| `recipients` | `MessageRecipients` recipient set |
| `channels` | Channel code list, constants in `MessageChannel` |
| `bizKeyBase` | Business idempotency key prefix; final `platform_msg.biz_key = bizKeyBase + ":" + channelCode` |
| `tenantCode` / `sender` | Tenant (defaults to geelato) and sender identity |
| Others | Channel extension attributes etc., see the source Javadoc |

## Channels and Adapters

SPI: `cn.geelato.dyn.common.message.channel.MessageChannelAdapter` (`channelCode()` + `resolve(MessageRequest)`), base class `AbstractMessageChannelAdapter`. The registry `MessageChannelRegistry` manages two groups:

| Channel | Path | Notes |
|---|---|---|
| `email` | Outbox (message center) | HTML and attachments supported |
| `sms` | Outbox (message center) | SMS |
| `bot` | Outbox (message center) | WeCom group robot (session-based) |
| `weixin_work_group` | Outbox (message center) | WeCom app / group messages |
| `weixin_work_robot` | Outbox (message center) | **Not yet usable**: the message center has no route mapping for this type yet and would reject the enqueue; enable once the route exists |
| `inapp` | **Direct** (bypasses the message center) | `InAppDirectChannelAdapter` synchronously POSTs to the community platform's in-app messaging (fixed `Authorization: SystemToken` key); with `enabled=false` the channel is skipped, no outbox fallback |

## Channel Configuration

Outbox and direct-channel settings are centralized in `geelato-dyn-common/src/main/resources/OSGI-INF/blueprint/geelato-common-message.xml` (adjust per environment at deployment):

```xml
<!-- Message center outbox: systemCode goes into the X-System-Code header, apiKey into the Authorization: Bearer header
     (only needed when the message center enables integration auth) -->
<bean id="messageCenterOutbox" class="...channel.MessageCenterOutbox">
    <property name="baseUrl" value="<message-center-url>"/>
    <property name="systemCode" value="geelato-dyn"/>
    <property name="apiKey" value="<apiKey>"/>
    <property name="timeoutMs" value="10000"/>
</bean>

<!-- In-app direct: communityToken is the community platform's SystemToken fixed key,
     matching the community's geelato.security.system-token.token (Bearer will not work) -->
<bean id="inAppDirectChannelAdapter" class="...channel.InAppDirectChannelAdapter">
    <property name="enabled" value="true"/>
    <property name="baseUrl" value="<community-platform-url>"/>
    <property name="communityToken" value="<SystemToken>"/>
    <property name="timeoutMs" value="10000"/>
</bean>
```

:::note How to change these settings
Outbox URL, direct URL and tokens are bean properties in this XML — i.e. [Blueprint bean configuration](configuration.md#blueprint-bean-configuration): after editing, rebuild and republish the common bundle and reinstall the feature; all business modules pick it up at once.
:::

## Adding a Channel

Implement `MessageChannelAdapter` (or extend `AbstractMessageChannelAdapter`), add the new adapter to the `MessageChannelRegistry` adapters / directChannels list and republish common; business modules can then select it via a `MessageChannel` constant.

## Recommended Reading

- [Built-in Capabilities](capabilities.md) — user directory (batch recipient resolution) and other supporting capabilities
- [Unified Callback & Consumer Bus](callback-bus.md) — the reverse path: receiving third-party callbacks
