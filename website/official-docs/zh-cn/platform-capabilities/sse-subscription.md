---
title: SSE 订阅推送
sidebar_label: SSE 订阅推送
---

# SSE 订阅推送

平台基于 Server-Sent Events（SSE）提供消息订阅与推送能力，支持服务端在数据变更后将事件实时下发到前端。订阅者可按业务主题精确订阅，也可以一次性订阅全部主题的全量消息。

## 适用场景

- 前端实时刷新字典、页面配置、流程状态等数据缓存。
- 聚合通知、监控大盘、调试页需要接收平台所有主题的事件流。

## 订阅端点

订阅端点统一挂在 `/subscribe` 下，返回 `text/event-stream` 长连接流：

| 端点 | 说明 |
| --- | --- |
| `GET /subscribe/{topic}` | 订阅指定主题。`topic` 为非空字符串，否则返回 `400 Bad Request`。 |
| `GET /subscribe/topic/all` | 订阅全部主题。平台向任意主题推送的消息都会复制一份到该流。 |

### 认证

订阅端点受 `DefaultSecurityInterceptor` 保护，请求头需携带有效的 `Authorization`，支持的凭证形式包括 `JWTBearer`、`Bearer`（OAuth2）、`Anonymous` 等。订阅本身不区分主题权限——任意已认证用户均可订阅任意主题或全量主题流。

### 连接生命周期

每个连接的超时时间为 **30 分钟**。连接完成（completion）、超时（timeout）或异常（error）后，订阅关系会被自动清理；推送过程中识别到失效连接时也会即时移除，避免内存泄漏。客户端断线后，浏览器 `EventSource` 会按服务端下发的 `retry: 3000`（毫秒）自动重连。

## 客户端订阅

服务端未在事件流中设置自定义 `event:` 名称，因此消息统一以默认 `message` 事件下发，客户端通过 `onmessage` 接收。消息体为 JSON 字符串，其结构由推送方决定。

### 订阅单个主题

```javascript
const source = new EventSource('/subscribe/upgrade_page_topic');

source.onmessage = (event) => {
  const payload = JSON.parse(event.data);
  console.log(payload);
  // 示例：{ "DATA": "UpgradePageEvent", "PAGE_ID": 12, "EXTEND_ID": 3 }
};
```

### 订阅全部主题

```javascript
const source = new EventSource('/subscribe/topic/all');

source.onmessage = (event) => {
  const payload = JSON.parse(event.data);
  console.log(payload);
};
```

## 服务端推送

推送通过服务端代码触发，**不提供 HTTP 推送端点**。调用 `SseHelper.push(...)` 即可将消息广播到对应主题的全部订阅者，同时同步复制到 `/subscribe/topic/all` 的全量订阅者。

### 推送 API

```java
// 构造消息：topic 为主题名称，data 为任意可序列化为 JSON 的对象
SseMessage message = new SseMessage("upgrade_page_topic", payload);
SseHelper.push(message);
```

`SseHelper` 主要方法：

| 方法 | 说明 |
| --- | --- |
| `SseHelper.push(SseMessage message)` | 向指定主题推送消息。`message.topic` 不能为空，否则抛出 `IllegalArgumentException`。 |
| `SseHelper.subscribe(String topic)` | 创建指定主题的 `SseEmitter`（供控制器返回）。 |
| `SseHelper.subscribeAll()` | 创建全量主题的 `SseEmitter`。 |
| `SseHelper.getActiveTopics()` | 返回当前有活跃订阅者的主题集合。 |

`SseMessage` 仅包含两个字段：

```java
public class SseMessage {
    private String topic;   // 主题名称
    private Object data;    // 负载，序列化为 JSON 下发
}
```

### 示例：在事件处理中推送

平台内置多处推送调用，通常由领域事件触发。以页面配置升级为例：

```java
@Override
public void handle() {
    Map<String, Object> data = new HashMap<>();
    data.put("DATA", getEventCode());          // "UpgradePageEvent"
    data.put("PAGE_ID", pageId);
    data.put("EXTEND_ID", extendId);
    SseHelper.push(new SseMessage("upgrade_page_topic", data));
}
```

业务侧推送消息时，建议在负载中约定一个标识字段（如示例中的 `DATA`），便于客户端区分事件类型。

## 平台内置主题

平台已通过领域事件接入以下主题，业务可直接订阅：

| 主题 | 触发时机 | 负载字段 |
| --- | --- | --- |
| `upgrade_dictionary_topic` | 数据字典项变更 | `DATA`、`DICT_ID` |
| `upgrade_page_topic` | 页面配置变更 | `DATA`、`PAGE_ID`、`EXTEND_ID` |
| `upgrade_state_workflow_topic` | 状态机流程定义变更 | `DATA`、`PROC_DEF_ID` |

## 兼容性

订阅能力向后兼容：新增的全量主题订阅与既有按主题订阅相互独立，不影响现有调用方；推送时按主题订阅者与全量订阅者各收到一份副本，互不干扰。
