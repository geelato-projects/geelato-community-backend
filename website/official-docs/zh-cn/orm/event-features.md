# ORM 事件特性

这篇文档说明 Geelato Framework 当前 ORM 层内置的事件机制，以及开发者如何基于这套事件抽象，在保存和删除链路上实现自己的定制逻辑。

当前事件能力位于：

- `cn.geelato.core.orm.event`

它不是独立的消息总线，也不是 Spring ApplicationEvent 的简单包装，而是 ORM 执行链路内部的轻量事件钩子。

## 解决什么问题

ORM 事件机制的目标，是把“通用 CRUD 执行”和“业务侧附加动作”解耦。

它适合承载这类需求：

- 保存前补充或校验额外字段
- 删除前做约束校验或软拦截
- 保存后做镜像表同步
- 保存后写审计日志、触发通知、做缓存刷新
- 删除后做下游清理、旁路索引维护、异步补偿

这样开发者不需要每次都侵入 `Dao` 或复制整套 CRUD 流程，只需要挂接事件监听器即可。

## 当前有哪些事件

当前 ORM 事件分为两大类：

- 保存事件
- 删除事件

并且每类都分成两个时机：

- `Before`
- `After`

因此一共可以理解为四种扩展点：

- `BeforeSaveEventListener`
- `AfterSaveEventListener`
- `BeforeDeleteEventListener`
- `AfterDeleteEventListener`

## 触发时机

事件是在 `Dao` 的实际保存、批量保存、多保存、删除、多删除链路中触发的。

也就是说，不管你是通过：

- `Dao.save(...)`
- `Dao.batchSave(...)`
- `Dao.multiSave(...)`
- `Dao.delete(...)`
- `Dao.multiDelete(...)`

还是通过更上层的 ORM Fluent DSL 最终落到这些执行路径，只要进入这些 ORM 写操作入口，就会命中事件机制。

## 执行模型

### 前置事件

`Before` 事件是同步执行的。

这意味着：

- 在真正执行 SQL 之前触发
- 可以修改上下文中的 `BoundSql`
- 可以做校验
- 可以直接抛异常阻断主流程

如果某个前置监听器抛出异常，当前保存或删除流程会直接失败。

因此 `Before` 事件适合做：

- 参数规范化
- SQL 调整
- 权限或状态校验
- 前置拦截

### 后置事件

`After` 事件是异步执行的。

当前实现通过固定线程池调度：

- 保存事件线程名前缀：`save-event-*`
- 删除事件线程名前缀：`delete-event-*`

默认线程池大小是：

- `4`

因此 `After` 事件的语义更接近：

- 主 SQL 已执行完成
- 监听器被异步调度
- 监听器异常只记录日志，不反向打断主流程

这类事件更适合做：

- 审计日志
- 镜像表同步
- 缓存刷新
- 非关键链路通知
- 异步旁路处理

## 事件上下文里有什么

### 保存事件上下文 `SaveEventContext`

保存事件上下文当前包含：

- `Dao dao`
- `SessionCtx sessionCtx`
- `IdEntity entity`
- `BoundSql boundSql`
- `SaveCommand command`
- `Map<String, Object> resultValueMap`
- `String eventId`
- `long startTime`

它的意义分别是：

- `dao`：允许监听器继续复用当前 ORM 执行能力
- `sessionCtx`：保存链路共享的会话级上下文
- `entity`：当入口来自实体保存时，可直接拿到实体对象
- `boundSql`：可以看到或修改本次最终执行的 SQL 与参数
- `command`：可读取实体名、值映射等 ORM 保存命令信息
- `resultValueMap`：主保存完成后的结果值映射
- `eventId`：本次事件链路唯一标识，方便日志关联
- `startTime`：事件开始时间，可用于统计耗时

### 删除事件上下文 `DeleteEventContext`

删除事件上下文当前包含：

- `Dao dao`
- `SessionCtx sessionCtx`
- `BoundSql boundSql`
- `DeleteCommand command`
- `int affectedRows`
- `String eventId`
- `long startTime`

它主要用于：

- 查看或改写删除 SQL
- 获取删除命令的实体信息
- 在删除后读取受影响行数
- 在日志或补偿流程里跟踪本次删除事件

## 监听器接口怎么理解

### `SaveEventListener`

保存监听器基础接口定义了：

- `beforeSave(SaveEventContext context)`
- `afterSave(SaveEventContext context)`
- `supports(...)`
- `enabled(...)`

需要特别注意：

- `supports(...)` 默认返回 `false`
- `enabled(...)` 默认返回 `false`

这意味着如果开发者不显式覆写这两个方法，监听器即使注册了也不会实际生效。

### `DeleteEventListener`

删除监听器基础接口定义了：

- `beforeDelete(DeleteEventContext context)`
- `afterDelete(DeleteEventContext context)`
- `supports(...)`
- `enabled(...)`

同样地：

- 默认不会自动启用
- 必须显式声明自己“支持当前上下文”且“当前启用”

## 监听器如何注册

当前不是通过 Spring 自动发现，而是通过静态管理器注册。

保存事件使用：

- `SaveEventManager.registerBefore(...)`
- `SaveEventManager.registerAfter(...)`
- `SaveEventManager.registerBeforeIfAbsent(...)`
- `SaveEventManager.registerAfterIfAbsent(...)`

删除事件使用：

- `DeleteEventManager.registerBefore(...)`
- `DeleteEventManager.registerAfter(...)`
- `DeleteEventManager.registerBeforeIfAbsent(...)`
- `DeleteEventManager.registerAfterIfAbsent(...)`

同时也支持：

- 注销监听器
- 清空监听器
- 替换执行器 `setExecutor(...)`

这说明当前事件机制是：

- 全局注册
- 进程内生效
- 以 JVM 为边界的监听器模型

## 当前内置示例：只读影子表监听器

当前保存事件默认注册了一个示例监听器：

- `ReadonlyShadowTableListener`

它的作用是把保存 SQL 映射到对应的：

- `*_readonly`

影子表。

例如：

- `insert into xxx (...)`
- `update xxx set ...`

会被转换成：

- `insert into xxx_readonly (...)`
- `update xxx_readonly set ...`

不过当前这个监听器内部开关默认是关闭的：

- `READONLY_EVENT_ENABLED = false`

所以它更像一个“内置参考实现”，说明事件机制可以用来做：

- 只读镜像表同步
- 双写旁路
- 数据镜像维护

## 开发者可以怎么做定制

### 场景 1：保存前校验

如果你想在写库前做领域校验，可以实现：

- `BeforeSaveEventListener`

典型用途：

- 禁止某些状态下更新
- 校验跨字段约束
- 补充落库前的附加字段

示例：

```java
public class CustomerBeforeSaveListener implements BeforeSaveEventListener {
    @Override
    public void beforeSave(SaveEventContext context) {
        if ("crm_customer".equalsIgnoreCase(context.getCommand().getEntityName())) {
            Object code = context.getCommand().getValueMap().get("code");
            if (code == null || String.valueOf(code).isBlank()) {
                throw new IllegalArgumentException("客户编码不能为空");
            }
        }
    }

    @Override
    public void afterSave(SaveEventContext context) {
    }

    @Override
    public boolean supports(SaveEventContext context) {
        return context.getCommand() != null;
    }

    @Override
    public boolean enabled(SaveEventContext context) {
        return true;
    }
}
```

### 场景 2：保存后异步旁路处理

如果你想在主保存成功后做一些不阻断主链路的扩展动作，可以实现：

- `AfterSaveEventListener`

典型用途：

- 写审计日志
- 刷新缓存
- 推送变更通知
- 同步搜索索引

示例：

```java
public class CustomerAfterSaveListener implements AfterSaveEventListener {
    @Override
    public void beforeSave(SaveEventContext context) {
    }

    @Override
    public void afterSave(SaveEventContext context) {
        if ("crm_customer".equalsIgnoreCase(context.getCommand().getEntityName())) {
            System.out.println("customer changed, eventId=" + context.getEventId());
        }
    }

    @Override
    public boolean supports(SaveEventContext context) {
        return context.getCommand() != null;
    }

    @Override
    public boolean enabled(SaveEventContext context) {
        return true;
    }
}
```

### 场景 3：删除前拦截

如果你想在删除前做防护，可以实现：

- `BeforeDeleteEventListener`

典型用途：

- 禁止删除系统内置数据
- 校验是否存在下游引用
- 把物理删除改写为特殊删除策略

### 场景 4：删除后清理

如果你想在删除后做清理动作，可以实现：

- `AfterDeleteEventListener`

典型用途：

- 删除缓存
- 清理索引
- 删除旁路表数据
- 记录删除审计

## 推荐接入方式

当前更推荐业务方把监听器注册收口到一个明确的启动装配位置，而不是在很多零散位置随意注册。

例如：

- Spring 启动类初始化阶段
- 某个统一的 ORM 配置类
- 某个基础模块的静态初始化逻辑

这样可以保证：

- 监听器注册顺序可控
- 多个业务模块不会重复注册
- 环境切换时更容易控制启停

## 使用注意事项

### 1. `Before` 会阻断主流程

前置监听器异常会直接中断保存或删除，因此这里更适合放：

- 强约束
- 强校验
- 必须成功的前置处理

### 2. `After` 不适合承载强事务语义

后置监听器是异步执行的，因此不要把“必须和主事务完全一致”的动作只放在 `After` 里。

如果你的逻辑必须与主写操作强一致，更适合：

- 放在前置阶段处理
- 或者直接在主业务服务中显式编排事务

### 3. 注意线程与上下文边界

由于 `After` 事件在线程池里执行，开发者不能想当然依赖：

- 当前线程本地变量
- Web 请求上下文
- 尚未显式透传的安全上下文

如果监听器需要这些信息，应从事件上下文里取，或者在进入事件前显式复制所需数据。

### 4. 注意重复注册

虽然提供了 `register*IfAbsent(...)`，但如果业务方在多个位置各自 new 监听器实例，仍然可能造成重复生效。

推荐做法是：

- 统一管理监听器实例
- 统一注册入口

## 总结

Geelato Framework 当前 ORM 事件机制本质上是一套围绕 `Dao` 保存与删除链路的内置扩展点：

- 前置事件同步执行，适合校验和拦截
- 后置事件异步执行，适合通知和旁路处理
- 通过上下文对象把 SQL、命令、结果和会话信息暴露给监听器
- 开发者可以用统一监听器抽象，定制自己的审计、镜像、缓存、索引和校验逻辑

因此它特别适合作为：

- ORM 层统一扩展机制
- 多业务模块复用的领域钩子
- 避免侵入核心 CRUD 实现的定制入口

## 推荐继续阅读

- [ORM 总览](overview.md)
- [Fluent DSL 指引](fluent-dsl.md)
- [核心模块说明](../reference/core-modules.md)
