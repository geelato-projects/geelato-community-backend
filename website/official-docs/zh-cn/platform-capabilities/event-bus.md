# 事件总线

这篇文档说明 `geelato-web-common` 中的事件总线能力：

- `cn.geelato.web.common.event`

它当前由四个核心类组成：

- `BusinessEvent`
- `EventPublisher`
- `GlobalEventBus`
- `EventBusListener`

这套抽象的目标，是把“业务动作发生了什么”和“后续要附加做什么”解耦。

## 事件总线的用途

事件总线适合承载这类需求：

- 某个业务动作完成后做异步后处理
- 把核心业务逻辑和通知、审计、旁路同步解耦
- 在不侵入主控制器或主服务代码的前提下挂接扩展动作
- 为 SSE、消息通知、审计日志等能力提供统一触发点

和 ORM 事件相比，它更偏向：

- Web / 业务层事件
- 应用级事件
- 领域动作完成后的异步扩展

而不是只围绕底层 `Dao` 保存删除。

## 当前实现结构

### `BusinessEvent`

这是所有业务事件的抽象基类。

它当前提供：

- `getEventCode()`
- `handle()`
- `sourceClass`
- `sourceMethod`

其中：

- `getEventCode()` 用于定义事件标识
- `handle()` 用于定义事件被消费时真正执行的逻辑
- `sourceClass` 和 `sourceMethod` 会在发布时自动记录来源调用位置

因此一个事件对象不仅是“数据载体”，也是“处理动作的封装单元”。

### `EventPublisher`

这是统一发布入口。

开发者通过：

- `EventPublisher.publish(event)`

发布业务事件。

它会：

- 检查 `GlobalEventBus` 是否已初始化
- 打印事件类型、事件标识和发布位置日志
- 委托 `GlobalEventBus` 真正发布

### `GlobalEventBus`

这是对 Spring 事件广播器的统一包装。

当前内部依赖：

- `ApplicationEventMulticaster`

发布逻辑是：

- `eventMulticaster.multicastEvent(event)`

因此当前事件总线底层并不是自定义消息队列，而是建立在 Spring 事件广播机制之上。

### `EventBusListener`

这是当前默认的全局监听器。

它使用：

- `@EventListener`
- `@Async("eventExecutor")`

监听 `BusinessEvent`，并调用：

- `event.handle()`

这意味着当前默认语义是：

- 发布事件后，由 Spring 事件系统接收
- 在异步线程池里执行事件处理逻辑
- 主业务线程不直接承载这些后处理动作

## 如何定义自己的事件

当前推荐方式是：

1. 新建一个类继承 `BusinessEvent`
2. 为事件补充需要的业务字段
3. 实现 `getEventCode()`
4. 在 `handle()` 中编写真正的消费逻辑

示例：

```java
public class OrderCreatedEvent extends BusinessEvent {
    private final String orderId;
    private final String userId;

    public OrderCreatedEvent(Object source, String orderId, String userId) {
        super(source);
        this.orderId = orderId;
        this.userId = userId;
    }

    @Override
    public String getEventCode() {
        return "order.created";
    }

    @Override
    public void handle() {
        // 在这里做审计、通知、推送、旁路同步等逻辑
    }

    public String getOrderId() {
        return orderId;
    }

    public String getUserId() {
        return userId;
    }
}
```

## 如何发布事件

定义好事件之后，在业务代码里直接调用：

```java
EventPublisher.publish(new OrderCreatedEvent(this, orderId, userId));
```

适合发布的位置通常包括：

- Controller 完成关键业务动作后
- Service 完成领域动作后
- 业务事务成功提交后的后续链路

## 如何订阅和消费

当前这套抽象里，最直接的消费方式不是再单独写多个 `@EventListener`，而是把消费逻辑收敛在事件自己的：

- `handle()`

方法里。

也就是说：

- 发布者负责 new 事件并 publish
- 总线负责广播
- 默认监听器负责异步调用 `event.handle()`
- 事件对象自己定义如何处理

这种模式的好处是：

- 使用门槛低
- 事件定义和默认处理紧密靠近

但也要注意：

- 如果一个事件需要多个完全独立的订阅者，就需要你进一步拆分事件职责，或扩展监听模型

## 什么时候适合用事件总线

更适合的场景：

- 审计日志
- 站内通知
- SSE 推送
- 非关键链路旁路同步
- 某个业务动作后的异步补偿

不太适合直接承载的场景：

- 必须强事务一致的核心写操作
- 必须同步返回结果给当前请求的逻辑

因为当前默认消费模型是异步的。

## 如何结合 SSE 实现服务端主动推送

事件总线和 SSE 非常适合组合使用。

推荐链路是：

1. 业务动作发生
2. 发布一个 `BusinessEvent`
3. 在事件的 `handle()` 中把事件转换为 SSE 消息
4. 通过 `SseHelper` 或 `SseEmitterManager` 向某个 topic 推送
5. 前端通过 `EventSource('/subscribe/{topic}')` 或 `/subscribe/topic/all` 接收

### 推荐模式

例如你定义：

- `OrderCreatedEvent`

它的 `handle()` 可以做：

- 组装 `SseMessage`
- 调用 `sendToTopic("order", message)`

示例结构：

```java
public class OrderCreatedEvent extends BusinessEvent {
    private final String orderId;

    public OrderCreatedEvent(Object source, String orderId) {
        super(source);
        this.orderId = orderId;
    }

    @Override
    public String getEventCode() {
        return "order.created";
    }

    @Override
    public void handle() {
        // 这里把业务事件转换成 SSE 推送
        // SseHelper.sendToTopic("order", new SseMessage(...));
    }
}
```

这样做的好处是：

- Controller / Service 不直接依赖 SSE 推送细节
- 业务事件与前端实时通知之间有清晰转换层
- 后续即使把 SSE 替换成 WebSocket 或消息队列，主业务代码也不需要一起改

## 事件总线与 SSE 的职责边界

建议这样区分：

- 事件总线：描述“系统里发生了什么”
- SSE：描述“要把什么实时推给客户端”

换句话说：

- 事件总线是后端内部传播机制
- SSE 是后端到前端的传输机制

最好的做法通常不是让 Controller 直接写一堆 SSE 推送代码，而是：

- 先发布业务事件
- 再在事件消费阶段决定是否转成 SSE

## 使用建议

### 1. 事件编码要稳定

`getEventCode()` 建议使用稳定、可读的命名，例如：

- `order.created`
- `user.login`
- `workflow.task.finished`

### 2. `handle()` 里避免承载超重逻辑

虽然当前消费是异步的，但也不建议在 `handle()` 里塞过重的长事务逻辑。

更适合：

- 审计
- 推送
- 同步
- 通知

### 3. 注意异常处理

如果事件处理逻辑本身依赖外部系统，应做好：

- 日志记录
- 降级处理
- 重试策略

### 4. 让 SSE 成为事件的下游，而不是上游

推荐是：

- 业务事件驱动 SSE

而不是：

- 为了 SSE 才定义业务流程

## 总结

当前事件总线本质上是建立在 Spring `ApplicationEventMulticaster` 之上的一个业务事件抽象：

- `BusinessEvent` 定义事件协议
- `EventPublisher` 作为统一发布入口
- `GlobalEventBus` 负责广播
- `EventBusListener` 负责异步消费并执行 `handle()`

开发者可以基于它：

- 定义自己的业务事件
- 在业务完成后异步发布
- 在 `handle()` 中实现审计、通知、同步和 SSE 推送

因此它非常适合作为：

- 平台应用层事件扩展点
- 后端内部传播机制
- SSE 主动推送的后端触发入口

## 推荐继续阅读

- [SSE订阅推送](sse-subscription.md)
- [流量染色](traffic-tagging.md)
- [认证鉴权](../authentication/security-authentication.md)
