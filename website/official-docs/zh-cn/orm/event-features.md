---
title: ORM 事件特性
sidebar_label: ORM 事件特性
---

# ORM 事件特性

本页说明 Geelato Framework ORM 层内置的事件机制：如何选择扩展点、实现监听器、统一注册并验证是否生效。

当前事件能力位于：

- `cn.geelato.core.orm.event`

它不是独立的消息总线，也不是 Spring ApplicationEvent 的简单包装，而是 ORM 执行链路内部的轻量事件钩子。

事件机制围绕 `Dao` 的写操作（save/delete/batchSave/multiSave/multiDelete）以及查询操作（query）展开，提供「前置同步、后置异步、事务感知、可控线程池、优先级」等能力，覆盖审计、校验、缓存、旁路同步、读拦截等场景。

## 适用场景

适合通过事件机制处理的需求：

- 保存前做校验、补字段、拦截非法写入。
- 保存后写审计日志、刷新缓存、做旁路同步。
- 删除前阻止误删。
- 删除后做清理动作。
- 查询前后做读审计、慢查询统计、缓存预热（查询事件）。
- 只在事务真正提交后才执行的副作用（事务感知 after 回调）。

以下需求不适合使用事件机制，应改用对应能力：

- 查询自动注入租户、权限、组织过滤 → [查询过滤与字段填充 SPI 扩展](../reference/spi-query-filter-and-save-fill-extension.md)
- 保存链路统一补默认审计字段 → [查询过滤与字段填充 SPI 扩展](../reference/spi-query-filter-and-save-fill-extension.md)
- 切换数据源 → [ORM / 数据源扩展](datasource-extension.md)

## 解决什么问题

ORM 事件机制的目标，是把“通用 CRUD 执行”和“业务侧附加动作”解耦。

这样开发者不需要每次都侵入 `Dao` 或复制整套 CRUD 流程，只需要挂接事件监听器即可。

## 当前有哪些事件

ORM 事件分为三大类，每类都分成 `Before`/`After` 两个时机：

| 事件类型 | 前置监听器 | 后置监听器 | 说明 |
|---|---|---|---|
| 保存 | `BeforeSaveEventListener` | `AfterSaveEventListener` | insert / update |
| 删除 | `BeforeDeleteEventListener` | `AfterDeleteEventListener` | delete |
| 查询 | `BeforeQueryEventListener` | `AfterQueryEventListener` | query（读拦截） |

此外，保存和删除还提供**事务感知**后置监听器：

- `TransactionalAfterSaveEventListener`：仅在事务提交/回滚后触发
- `TransactionalAfterDeleteEventListener`：同上

监听器还可通过**函数式接口**用 lambda 注册（详见下文“函数式 callback”）。

## 先决定该用哪一种

按下面判断选择监听器：

- 保存前强校验、强约束、强拦截：`BeforeSaveEventListener`
- 保存后异步通知、审计、缓存刷新：`AfterSaveEventListener`
- **必须保证只在事务提交后才执行的副作用**（如 ES 同步、避免读到回滚数据）：`TransactionalAfterSaveEventListener`
- 删除前防误删、引用校验：`BeforeDeleteEventListener`
- 删除后清缓存、删旁路数据、记审计：`AfterDeleteEventListener`
- 查询前后读审计、慢查询统计、缓存预热：`QueryEventListener`（`BeforeQueryEventListener` / `AfterQueryEventListener`）

最重要的区分：

- `Before` **同步**执行，异常会直接阻断主流程（异常是否阻断由监听器实现者决定，框架透传异常、不做全局开关）
- `After` **异步**执行，异常只记日志，不影响主流程
- `Transactional After` 在**事务提交/回滚后**触发，解决普通 `After`「异步、事务外、可能读到回滚数据」的问题

## 触发时机

事件在 `Dao` 的写操作与查询链路中触发：

- `Dao.save(...)` / `Dao.batchSave(...)` / `Dao.multiSave(...)`
- `Dao.delete(...)` / `Dao.multiDelete(...)`
- `Dao.queryList(...)` / `Dao.queryForMapList(...)`（查询事件）

不管是直接调用 `Dao`，还是通过 ORM Fluent DSL（`MetaFactory.insert/update/delete/query`）最终落到这些执行路径，只要进入这些 ORM 入口，就会命中事件机制。

## 执行模型

### 前置事件（Before）

`Before` 事件**同步**执行：

- 在真正执行 SQL 之前触发
- 可以修改上下文中的 `BoundSql`
- 可以做校验
- 可以直接抛异常阻断主流程

如果某个前置监听器抛出异常，当前保存/删除/查询流程会直接失败。

**异常契约**：框架对 `Before` 监听器抛出的异常采取**透传**策略（重新抛出给调用方），**不提供全局开关**。是否阻断业务由监听器实现者自行决定：

- 校验类监听器（需要拦截非法写入）：直接抛异常，由异常阻断主流程。
- 旁路类监听器（审计、埋点，不应阻断业务）：在方法内部 `try/catch` 自行吞掉异常。

> 提示：`Before` 监听器如果抛出非数据访问异常，`Dao.multiSave/multiDelete` 会确保事务回滚（不会出现事务悬挂）。

### 后置事件（After）

`After` 事件**异步**执行，通过线程池调度：

- 保存事件线程名前缀：`save-event-*`
- 删除事件线程名前缀：`delete-event-*`
- 查询事件线程名前缀：`query-event-*`

线程池是**可控的**（默认：核心线程 4 + 有界队列 1000 + `CallerRunsPolicy` 背压策略），可通过配置调整（详见下文“线程池配置”）。

`After` 的语义：

- 主 SQL 已执行完成
- 监听器被异步调度
- 监听器异常只记录日志，不影响主流程

适合做：审计日志、镜像表同步、缓存刷新、非关键链路通知、异步旁路处理。

### 事务感知后置事件（Transactional After）

普通 `After` 是异步、在事务提交前调度，监听器无法保证读到已提交数据（事务可能尚未 commit 或已回滚）。

实现 `TransactionalAfterSaveEventListener` / `TransactionalAfterDeleteEventListener` 的监听器：

- `afterCommit(context)`：仅在事务**真正提交后**触发；无事务时立即触发（视为已提交）
- `afterRollback(context)`：事务**回滚后**触发

框架在 `fireAfter` 时检测监听器是否实现了事务感知接口，若是则把回调登记到事件上下文，由 `EventTransactionSupport` 结合 Spring `TransactionSynchronizationManager` 在提交/回滚点触发。

适合做：ES 同步、强依赖提交数据的旁路处理（消除「读到回滚脏数据」风险）。

> 注意：事务感知 after 回调在 `Dao.save/delete/multiSave/multiDelete` 路径完整生效。使用时确保写操作经由 `Dao` 执行。

## 事件上下文里有什么

### 保存事件上下文 `SaveEventContext`

| 字段 | 类型 | 说明 |
|---|---|---|
| `dao` | `Dao` | 允许监听器复用当前 ORM 执行能力 |
| `sessionCtx` | `SessionCtx` | 保存链路共享的会话级上下文 |
| `entity` | `IdEntity` | 当入口来自实体保存时，可直接拿到实体对象 |
| `boundSql` | `BoundSql` | 可看到或修改本次最终执行的 SQL 与参数 |
| `command` | `SaveCommand` | 实体名、值映射等 ORM 保存命令信息 |
| `resultValueMap` | `Map` | 主保存完成后的结果值映射 |
| `eventId` | `String` | 本次事件链路唯一标识 |
| `startTime` | `long` | 事件开始时间 |
| `success` | `boolean` | SQL 是否执行成功（after 触发前置 true，失败为 false） |
| `exception` | `Throwable` | 失败时的异常 |
| `affectedRows` | `int` | 受影响行数 |

便捷判断（避免解析 SQL）：

- `getOperType()`：操作类型（`CommandType.Insert` / `CommandType.Update`）
- `isInsert()` / `isUpdate()`：是否新增/更新

事务感知回调注册入口：

- `onCommit(Runnable)` / `onRollback(Runnable)`

### 删除事件上下文 `DeleteEventContext`

| 字段 | 说明 |
|---|---|
| `dao` / `sessionCtx` / `boundSql` / `command` / `affectedRows` / `eventId` / `startTime` | 同保存上下文 |
| `success` / `exception` | 执行结果（同保存上下文） |

事务感知回调入口：`onCommit(Runnable)` / `onRollback(Runnable)`。

### 查询事件上下文 `QueryEventContext`

| 字段 | 说明 |
|---|---|
| `dao` / `sessionCtx` / `boundSql` / `command` | ORM 执行能力与会话信息 |
| `entityType` | 查询目标实体类型（原生 SQL 查询时可能为 null） |
| `success` / `exception` | 查询是否成功 |
| `rowCount` | 返回行数（after 阶段填充，便于慢查询/大结果集统计） |
| `eventId` / `startTime` | 链路标识与开始时间 |

## 监听器接口怎么理解

`SaveEventListener` / `DeleteEventListener` / `QueryEventListener` 定义：

- `beforeXxx(context)` / `afterXxx(context)`
- `enabled(context)`：配置级粗开关，应廉价（只读 properties/常量），判断监听器是否全局启用
- `supports(context)`：单次事件级细匹配，可查元数据/解析 SQL，判断是否处理本次特定事件
- `getOrder()`：优先级，值小先执行（默认 0）

**需要特别注意**：

- `enabled(...)` 与 `supports(...)` 默认返回 `false`
- 二者均须为 `true` 才触发回调
- 不显式覆写这两个方法，监听器即使注册了也不会生效

`enabled` 与 `supports` 的语义约定：

- `enabled`：粗粒度，判配置（如 `properties.isEnabled()`、常量开关），每次触发都求值但应廉价
- `supports`：细粒度，判单次事件（如按实体名/表名过滤），可查元数据

## 监听器如何注册

当前通过静态管理器注册（全局、进程内、以 JVM 为边界），推荐在 Spring 配置类集中注册：

保存事件：`SaveEventManager.registerBefore(...)` / `registerAfter(...)` / `registerBeforeIfAbsent(...)` / `registerAfterIfAbsent(...)`

删除事件：`DeleteEventManager.registerBefore(...)` / `registerAfter(...)` / `registerBeforeIfAbsent(...)` / `registerAfterIfAbsent(...)`

查询事件：`QueryEventManager.registerBefore(...)` / `registerAfter(...)` / `registerBeforeIfAbsent(...)` / `registerAfterIfAbsent(...)`

注销：`unregisterBefore(...)` / `unregisterAfter(...)`（推荐在 `@PreDestroy` 调用，防热部署/上下文刷新累积泄漏）。

注册按 `getOrder()` 升序插入，值小先执行。

### 函数式 callback（推荐用于轻量场景）

为避免实现空标记接口的样板代码，提供函数式 callback 接口，可用 lambda 注册：

```java
import cn.geelato.core.orm.event.SaveEventManager;

// 等价于实现 BeforeSaveEventListener，但无需写空的 afterSave/enabled/supports
SaveEventManager.registerBeforeCallback((ctx) -> {
    // 保存前逻辑
});
// 可指定优先级
SaveEventManager.registerBeforeCallback((ctx) -> { /* ... */ }, 10);
```

可用的 callback 接口：`BeforeSaveCallback` / `AfterSaveCallback` / `BeforeDeleteCallback` / `AfterDeleteCallback`（均在 `cn.geelato.core.orm.event.callback` 包）。

注册入口：`SaveEventManager.registerBeforeCallback(...)` / `registerAfterCallback(...)`，`DeleteEventManager` 同名方法。

> 函数式 callback 与传统监听器接口并存，不互相替代；新代码轻量场景推荐 callback。

## 线程池配置

后置事件异步线程池由 `geelato-orm` 的 `OrmEventAutoConfiguration` 自动装配，可通过配置调整：

```properties
# 关闭事件线程池自动装配（回退到管理器默认池）
geelato.orm.event.enabled=true
# 保存事件线程池
geelato.orm.event.save.pool-size=4
geelato.orm.event.save.queue-capacity=1000
# 删除事件线程池
geelato.orm.event.delete.pool-size=4
geelato.orm.event.delete.queue-capacity=1000
```

特性：

- 有界队列 + `CallerRunsPolicy`（队列满时由提交线程执行，背压、不丢任务、不 OOM）
- 守护线程，容器销毁时优雅关闭（`@PreDestroy` 调用 `shutdown`）
- 也可通过 `SaveEventManager.setExecutor(...)` / `DeleteEventManager.setExecutor(...)` 程序化替换（替换前会优雅关闭旧池）

## 最短接入步骤

### 第 1 步：选一个扩展点

- 会影响主写入正确性的逻辑，优先放 `Before`
- 只是旁路增强的逻辑，优先放 `After`
- 必须等事务提交后才执行的逻辑，用 `Transactional After`

### 第 2 步：实现监听器

“保存前校验客户编码”的最小示例：

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

容易漏掉的两个点：`supports(...)` 与 `enabled(...)` 默认都是 `false`，不覆写则注册了也不执行。

### 第 3 步：在统一入口注册监听器

```java
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Configuration
public class OrmEventConfiguration {

    private final CustomerBeforeSaveListener beforeListener = new CustomerBeforeSaveListener();

    @PostConstruct
    public void registerListeners() {
        SaveEventManager.registerBeforeIfAbsent(beforeListener);
    }

    @PreDestroy
    public void unregisterListeners() {
        // 生命周期管理：容器销毁时注销，防热部署泄漏
        SaveEventManager.unregisterBefore(beforeListener);
    }
}
```

删除/查询链路改成对应的 `DeleteEventManager` / `QueryEventManager`。

### 第 4 步：实际触发并验证

```java
String id = MetaFactory.insert("Customer")
        .value("name", "Demo")
        .value("code", "C001")
        .save();
```

验证时优先看三件事：

- 监听器方法是否真的被触发
- `supports(...)` 与 `enabled(...)` 是否返回 `true`
- 注册入口是否只执行了一次（避免重复注册）

## 当前内置示例：只读影子表监听器

保存事件默认注册了 `ReadonlyShadowTableListener`，把保存 SQL 映射到 `*_readonly` 影子表。其内部开关默认关闭（`READONLY_EVENT_ENABLED = false`），作为内置参考实现。

## 开发者可以怎么做定制

### 场景 1：保存前校验（Before）

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
    @Override public void afterSave(SaveEventContext context) {}
    @Override public boolean supports(SaveEventContext context) { return context.getCommand() != null; }
    @Override public boolean enabled(SaveEventContext context) { return true; }
}
```

适合：必填校验、状态流转校验、写入前参数规范化、必须阻断主流程的约束。

### 场景 2：保存后异步旁路处理（After）

```java
public class CustomerAfterSaveListener implements AfterSaveEventListener {
    @Override public void beforeSave(SaveEventContext context) {}
    @Override
    public void afterSave(SaveEventContext context) {
        if ("crm_customer".equalsIgnoreCase(context.getCommand().getEntityName())) {
            System.out.println("customer changed, eventId=" + context.getEventId()
                    + ", success=" + context.isSuccess());
        }
    }
    @Override public boolean supports(SaveEventContext context) { return context.getCommand() != null; }
    @Override public boolean enabled(SaveEventContext context) { return true; }
}
```

适合：审计日志、缓存刷新、旁路通知、搜索索引同步。

### 场景 3：事务感知后置（Transactional After）

适合必须等事务提交后才执行的副作用（如 ES 同步，避免读到回滚数据）：

```java
public class CustomerEsSyncListener implements TransactionalAfterSaveEventListener {
    @Override public void beforeSave(SaveEventContext context) {}
    @Override public void afterSave(SaveEventContext context) {} // 老的异步 after，留空

    @Override
    public void afterCommit(SaveEventContext context) {
        // 仅在事务提交后执行，此时数据已落库
        if ("crm_customer".equalsIgnoreCase(context.getCommand().getEntityName())) {
            System.out.println("customer committed, sync to ES, eventId=" + context.getEventId());
        }
    }

    @Override
    public void afterRollback(SaveEventContext context) {
        // 事务回滚时触发，可用于清理/告警
        System.out.println("customer save rolled back, skip ES sync");
    }

    @Override public boolean supports(SaveEventContext context) { return context.getCommand() != null; }
    @Override public boolean enabled(SaveEventContext context) { return true; }
}
```

注册方式与普通监听器相同：`SaveEventManager.registerAfterIfAbsent(listener)`（它仍继承 `AfterSaveEventListener`）。

### 场景 4：删除前拦截（Before）

```java
public class CustomerBeforeDeleteListener implements BeforeDeleteEventListener {
    @Override
    public void beforeDelete(DeleteEventContext context) {
        if ("crm_customer".equalsIgnoreCase(context.getCommand().getEntityName())) {
            throw new IllegalStateException("客户数据不允许直接删除，请先走业务注销流程");
        }
    }
    @Override public void afterDelete(DeleteEventContext context) {}
    @Override public boolean supports(DeleteEventContext context) { return context.getCommand() != null; }
    @Override public boolean enabled(DeleteEventContext context) { return true; }
}
```

### 场景 5：查询读审计/慢查询统计（Query）

```java
public class SlowQueryListener implements AfterQueryEventListener {
    @Override public void beforeQuery(QueryEventContext context) {}
    @Override
    public void afterQuery(QueryEventContext context) {
        long cost = System.currentTimeMillis() - context.getStartTime();
        if (cost > 500) {
            System.out.println("slow query, cost=" + cost + "ms, rows=" + context.getRowCount()
                    + ", eventId=" + context.getEventId());
        }
    }
    @Override public boolean supports(QueryEventContext context) { return true; }
    @Override public boolean enabled(QueryEventContext context) { return true; }
}
```

注册：`QueryEventManager.registerAfterIfAbsent(listener)`。

### 场景 6：函数式 callback（轻量注册）

```java
@PostConstruct
public void register() {
    // 用 lambda 注册保存前校验，无需写实现类
    SaveEventManager.registerBeforeCallback(ctx -> {
        if (ctx.isUpdate() && "crm_customer".equalsIgnoreCase(ctx.getCommand().getEntityName())) {
            // 更新前校验
        }
    });
}
```

## 推荐接入方式

推荐把监听器注册收口到一个明确的启动装配位置：

- Spring 启动类初始化阶段
- 某个统一的 ORM 配置类
- 某个基础模块的静态初始化逻辑

建议做法：

1. 每个监听器单独一个类（或用函数式 callback）
2. 在统一配置类里声明/注册
3. 在统一初始化入口里调用 `register*IfAbsent(...)`
4. 在 `@PreDestroy` 里调用 `unregister*`（生命周期管理）
5. 不要在业务 Service 方法里临时注册

## 一步一步的排障顺序

写好了监听器但“没有生效”，按这个顺序排查：

1. 看是否真的进入了 ORM 的写/查询链路
2. 看监听器是否已注册到对应 `EventManager`
3. 看 `supports(...)` 是否返回 `true`
4. 看 `enabled(...)` 是否返回 `true`
5. 看逻辑是不是写在 `After`，而你却期待它阻断主流程
6. 看是否出现重复注册，导致执行多次
7. （事务感知 after）确认写操作经由 `Dao` 执行，且回调在事务提交后才触发

## 使用注意事项

### 1. `Before` 会阻断主流程

前置监听器异常会中断主流程。框架对异常**透传、不加全局开关**：需要拦截就抛异常；不需要阻断就自行 `try/catch` 吞掉。

### 2. 普通 `After` 不适合承载强事务语义

后置监听器异步执行且在事务提交前调度，不要把“必须和主事务完全一致”的动作只放在普通 `After`。需要强事务一致请用 `TransactionalAfter*` 监听器，或显式编排事务。

### 3. 注意线程与上下文边界

`After` 事件在线程池执行，不能想当然依赖当前线程本地变量、Web 请求上下文。需要这些信息应从事件上下文取，或在进入事件前显式复制。

### 4. 注意重复注册与生命周期

提供 `register*IfAbsent(...)` 防重复，但若多处各自 new 实例仍可能重复生效。推荐统一管理实例 + 统一注册 + `@PreDestroy` 注销（尤其热部署/上下文刷新场景）。

### 5. 不要把查询规则塞进事件里

ORM 事件适合写/读操作链路的附加动作。查询自动追加租户/权限过滤、保存自动补字段，应优先用 SPI，不要塞进事件监听器。

### 6. 插入与更新的区分

`SaveCommand` 同时覆盖 insert/update。监听器可用 `context.isInsert()` / `context.isUpdate()` / `context.getOperType()` 区分，无需解析 SQL 字符串。

## 完整示例工程

平台在 `geelato-hello-example` 仓库提供了事件机制的可运行示例工程：

- `geelato-sample-orm-event`

它基于最轻量的 `geelato-orm`（H2 内存库），演示：保存前校验、保存后旁路、事务感知 after、查询慢查询统计、函数式 callback、优先级、执行结果回传。运行方式见该工程 README。

## 总结

Geelato Framework ORM 事件机制是一套围绕 `Dao` 写/查询链路的内置扩展点：

- 前置事件同步执行，适合校验和拦截（异常透传，是否阻断由实现者决定）
- 后置事件异步执行，适合通知和旁路处理（可控线程池 + 背压）
- 事务感知后置事件，适合必须等提交后才执行的强一致副作用
- 查询事件，适合读审计/慢查询/缓存预热
- 函数式 callback，轻量注册免样板
- 优先级、生命周期管理、执行结果回传等增强

它特别适合作为：ORM 层统一扩展机制、多业务模块复用的领域钩子、避免侵入核心 CRUD 的定制入口。

## 推荐继续阅读

- [ORM 总览](overview.md)
- [Fluent DSL 指引](fluent-dsl.md)
- [ORM / 数据源扩展](datasource-extension.md)
- [查询过滤与字段填充 SPI 扩展](../reference/spi-query-filter-and-save-fill-extension.md)
- [核心模块说明](../reference/core-modules.md)
