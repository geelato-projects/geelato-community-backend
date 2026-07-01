# SSE订阅推送

这篇文档说明平台侧如何同时支持：

- 按主题订阅 SSE 消息
- 订阅全部主题的 SSE 消息

目标是让前端既可以按业务主题精确订阅，也可以在监控、聚合通知、调试页面里一次性接收所有主题推送。

## 当前能力

当前平台已经有按主题订阅入口：

- `GET /subscribe/{topic}`

同时也预留了“全部主题订阅”入口：

- `GET /subscribe/topic/all`

但该入口在设计方案形成时仍是占位状态，因此需要补齐完整实现。

## 设计目标

### 按主题订阅

保留并完善：

- `GET /subscribe/{topic}`

要求：

- 返回 `text/event-stream`
- `topic` 不能为空
- 生命周期结束时自动清理订阅关系

### 订阅全部主题

新增完整实现：

- `GET /subscribe/topic/all`

这个入口返回 `SseEmitter`，客户端可直接使用：

```javascript
const source = new EventSource('/subscribe/topic/all');
```

这样可以接收所有主题广播出来的消息。

## 核心实现点

### 控制器层

控制器建议显式声明：

```java
produces = "text/event-stream"
```

并提供两个订阅入口：

- 主题订阅：`/subscribe/{topic}`
- 全部主题订阅：`/subscribe/topic/all`

### 管理器层

在 `SseEmitterManager` 中增加“全局订阅者”集合，例如：

- `allSubscribers`

并补齐：

- `subscribeAll()`
- 生命周期回调清理
- 发送失败时移除失效订阅

### 广播策略

当调用 `sendToTopic(topic, message)` 时，应同时广播给：

- 当前 `topic` 的订阅者
- 全部主题订阅者

这样既不破坏现有按主题能力，也能让聚合订阅自然生效。

## 推荐实现结构

### `SseEmitterManager`

负责：

- 管理 topic -> emitters 的映射
- 管理全部主题订阅者集合
- 统一注册 `onCompletion`、`onTimeout`、`onError`
- 在发送失败时做回收

### `SseHelper`

负责对管理器做轻量封装，建议同时提供：

- `subscribe(String topic)`
- `subscribeAll()`

保持统一调用风格。

### `SseController`

负责暴露 HTTP 入口：

- `GET /subscribe/{topic}`
- `GET /subscribe/topic/all`

并向外返回 `SseEmitter`。

## API 说明

### 按主题订阅

- 路径：`GET /subscribe/{topic}`
- 返回类型：`text/event-stream`
- 适用场景：只监听某个业务主题，如订单、通知、任务状态等

客户端示例：

```javascript
const source = new EventSource('/subscribe/news');
```

### 全部主题订阅

- 路径：`GET /subscribe/topic/all`
- 返回类型：`text/event-stream`
- 适用场景：聚合通知、调试监控、平台总线观察

客户端示例：

```javascript
const source = new EventSource('/subscribe/topic/all');
```

## 验证建议

即使不启动整站，也建议至少做两类验证。

### 管理器层验证

验证 `SseEmitterManager`：

- 创建一个主题订阅者
- 创建一个全部主题订阅者
- 调用 `sendToTopic(topic, message)`
- 确认两个订阅关系都能被正确投递

### 代码审查检查点

- 生命周期回调是否会正确清理连接
- 发送失败是否只移除当前失效订阅者
- 空集合时是否及时清理，避免内存膨胀

## 兼容性说明

这项能力不改变既有订阅路径，只是在现有基础上补齐：

- 全部主题订阅能力
- 更完整的清理与广播逻辑

因此对已有按主题订阅调用方是兼容增强，而不是破坏性调整。
