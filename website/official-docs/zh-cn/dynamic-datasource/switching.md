---
title: 数据源切换方式
sidebar_label: 切换方式
---

# 数据源切换方式

本页说明数据源切换的全部途径：哪些执行入口具备切库能力、`Dao` 调用如何自动选源、手工切库的 API，以及**事务内切库的限制**——这是使用动态数据源前最需要了解的边界。

切换分两类：

- **自动切库**：实体已声明归属（见[实体绑定](entity-binding.md)），调用时切面自动路由，业务代码零感知
- **手工切库**：调用时显式指定数据源，覆盖实体声明

两类都依赖同一个底层机制：路由数据源 `DynamicRoutingDataSource` 在执行时读取线程上下文（`DynamicDataSourceHolder`，一个 `ThreadLocal<String>`）中的数据源 key，再取出对应的物理数据源。key 对应的数据源未创建时会触发懒加载建池。

## 前提：执行入口必须是 `dynamicDao`

切库能力取决于 Dao 绑定的数据源，这是最常见的"为什么没切库"的原因：

| Dao Bean | 数据源 | 能否切库 |
|---|---|---|
| `primaryDao` | 直连物理主库 | **恒不路由**——任何 key 对它无效 |
| `dynamicDao` | `DynamicRoutingDataSource` 路由源 | 唯一能消费路由 key 的 Dao |

ORM 运行时的 Dao 绑定规则（`OrmDaoResolver`，高 → 低）：

1. `geelato.orm.dao-bean-name` 显式配置
2. `dynamicDao`（容器中存在即优先绑定——否则切库不生效）
3. `primaryDao`
4. 唯一的 Dao Bean（多个且无标准名时报错，要求显式配置）

典型工程（使用 Starter 且配置了 `spring.datasource.primary.jdbc-url`）中 `dynamicDao` 总会存在，ORM 自动绑定它，无需关心。**但若代码绕过 ORM 直接注入了 `primaryDao`，切库对这批调用永远无效**——请注入 `dynamicDao`。

字段注入时配合 `@UseDynamicDataSource` 注解：

```java
public class OrderService {
    @UseDynamicDataSource
    private Dao dynamicDao; // 按 "dynamic" + 字段类型简名 解析 bean
}
```

## 自动切库：`Dao` 调用时如何选源

`DataSourceInterceptor` 切面拦截所有 `Dao` 方法（`cn.geelato.core.orm.Dao.*(..)`），在执行前完成选源：

1. **识别实体**：按参数顺序逐个检查，识别出实体名
2. **解析数据源 key**：按优先级链裁决（见[实体绑定](entity-binding.md)）
3. **设置线程上下文 key**，执行方法
4. **finally 恢复**外层 key——保护嵌套调用

第 1 步的实体识别规则（按参数顺序，识别失败继续看下一个参数）：

| 参数形态 | 识别方式 | 典型方法 |
|---|---|---|
| `BoundPageSql` / `BoundSql` | 取命令携带的 entityName | 分页查询、MQL 执行 |
| 带 `@Entity` 注解的 `Class` | 取注解 name（为空取类简名） | `querys(clazz, ...)` 等 |
| 非空 `List` | 取首元素按同样规则递归识别 | `batchSave` / `multiSave` / `multiDelete` |
| 带 `@Entity` 注解的实例 | 同上 | `insert` / `save` / `update` |

全部无法识别时实体名为空，直接走兜底链（注解作用域默认 > 外层显式 key > 请求级覆盖 > 平台默认 > primary）。

识别出实体后，按[实体绑定页的优先级链](entity-binding.md)解析；解析出的 key 若对应数据源未注册，直接硬失败（抛出 `IllegalStateException`，拒绝静默回退主库）——**永远不会路由到不存在的数据源，也不会悄悄落到别的库**。

## 手工切换：三种 API

业务代码对线程上下文 key 的写入统一收敛在 `cn.geelato.datasource.DynamicDataSourceScope`（geelato-orm 提供）——它是数据源切换的**唯一出入口**。`DynamicDataSourceHolder`（一个 `ThreadLocal<String>`）只是它底层的承载机制，业务代码不应直接读写。

### 1. 代码块作用域：`DynamicDataSourceScope.withDataSource`

块内生效、结束恢复，适合代码级临时切库：

```java
List<Map<String, Object>> rows = DynamicDataSourceScope.withDataSource("biz_db", () ->
        dao.querys(SomeEntity.class));
```

- 正常或异常结束都会恢复进入前的原值（finally 语义内置），无需手工清理
- MQL 执行器、`DataSourceInterceptor` 切面、异步任务等框架路径均走此语义
- connectId 为空时不动当前 key，直接执行

### 2. 请求内默认库：`DynamicDataSourceScope.switchTo`

设置后持续生效（直至下次切换或请求结束），适合在请求入口处整段切库：

```java
// 在 Controller / Service 方法内：当前请求线程后续未命中实体映射的调用走 biz_db
DynamicDataSourceScope.switchTo("biz_db");
```

- 参数为空直接抛出 `IllegalArgumentException`，不会静默忽略
- 设置的是线程级 key，对后续所有**未命中实体映射**的调用生效；命中实体映射的调用仍以实体声明的数据源为准（实体映射优先级更高，见[实体绑定](entity-binding.md)）
- 请求结束由 web 层清理安全网兜底回收：`DataSourceContextCleanupFilter`（order=-100，位于过滤链最外层）在 finally 中同时清理线程 key 与请求级覆盖上下文，线程池复用不会把 key 带到下一个请求
- 平台 `BaseController` / `BaseService` 保留的 `switchDbByConnectId("biz_db")` 即本方法的薄委托，语义完全一致

### 3. Fluent DSL：`useDataSource(...)`

ORM 查询链式 API 上指定本次操作的数据源，**仅对本次调用生效**，调用结束自动恢复，无需手工清理：

```java
// 查询
List<Map<String, Object>> orders = MetaFactory.query("demo_order")
        .useDataSource("biz_db")
        .list();

// 插入（实体实例入口同理：MetaFactory.insert(entity).useDataSource("biz").save()）
MetaFactory.insert(DemoOrder.class)
        .useDataSource("biz_db")
        .save(order);
```

DSL 层面的覆盖关系：**显式 `useDataSource(...)` > 实体 connectId 声明 > 请求级覆盖 > 平台默认**。即实体已绑定库时，单次调用仍可用 `useDataSource` 临时指向另一个库。

## 嵌套调用的语义

四层保护确保嵌套切库不互相污染：

- **拦截器 finally 恢复**：内层 `Dao` 调用结束后，线程上下文 key 恢复为进入前的值，外层设置的 key 不受影响
- **代码块作用域**：`DynamicDataSourceScope.withDataSource` 块内生效，正常/异常结束都恢复原值——executor 侧的 DSL `useDataSource` 同样经它实现（设置 → 执行 → finally 恢复）
- **注解默认值即清即走**：类/方法级 `@UseDynamicDataSource` 的作用域默认值在方法结束后清理，不会泄漏到线程复用的下一个请求
- **清理安全网兜底**：`switchTo` 设置的请求内 key 与请求级覆盖上下文，在请求结束时由 `DataSourceContextCleanupFilter`（order=-100）统一回收，不依赖调用方自觉

因此典型嵌套场景的语义是：

```java
DynamicDataSourceScope.switchTo("biz_db"); // 外层切到 biz_db（等价于 switchDbByConnectId）
dao.querys(SomeEntity.class, ...);         // SomeEntity 未声明归属 → 沿用 biz_db（第 3 级兜底）
dao.querys(OtherEntity.class, ...);        // OtherEntity 声明了 connectId="order_db" → 路由 order_db（实体映射优先）
dao.querys(ThirdEntity.class, ...);        // ThirdEntity 未声明归属 → 回到 biz_db（内层调用已恢复外层 key）
```

## 请求级覆盖：归档数据在线查询

除以上代码级切换外，还有一条**请求级**的默认源覆盖通道：前端在请求头携带 `X-Gl-Archive-Query: true`，该请求内所有**无显式归属**实体的读操作自动路由到归档库。底层是 geelato-orm 提供的中性请求级数据源覆盖能力（`DataSourceOverrideContext`），geelato-web-platform 承载归档语义与请求过滤器。要点：

- **只切"无显式归属"的实体**：无 `connectId` 且 catalog 未映射的业务实体才被覆盖；显式归属实体（用户/字典/元数据等）保持原路由，页面框架照常工作
- **任何显式指定不被劫持**：实体归属、注解、DSL `useDataSource`、`withDataSource` 代码块都优先于请求级覆盖
- **只读**：归档模式下对无归属实体的写操作直接硬失败
- **硬失败**：归档数据源未配置时，携带该头的请求直接报错，不静默降级

完整配置、约束与前端契约见[归档数据在线查询](../archive/overview.md)。

## 事务内切库的限制（重要）

**开启事务后，数据源在事务开始时就已绑定，事务内再切换 key 不生效。**

原因：`DataSourceTransactionManager` 在事务开始（`doBegin`）时从路由数据源借出**第一个物理连接**并绑定到当前事务。此后线程上下文再改 key，事务内的操作仍走这个已绑定的连接。

具体表现：

- Spring `@Transactional` 方法内先操作 A 库再切 B 库：B 库操作实际仍落在 A 库连接上
- ORM 的 `batchSave(transaction = true)`：**按首条命令的 connectId 决定整批事务的库**，批内其他命令的 connectId 不改变事务归属

实践建议：

- 跨库操作**不要包在同一个事务里**——按库拆分事务边界，或接受最终一致性方案
- 同一事务内的所有操作，确保它们的实体归属（或显式指定的 connectId）一致
- 确有跨库强一致需求时，评估 JTA / Seata 预留能力（默认未开启，见[总览](overview.md)）

## 多租户场景

切库本身走上述机制（实体绑定 / 手工切换），与租户无关。租户数据隔离的常见做法是"切到某个租户数据源后自动追加租户过滤条件"，这通过 Fluent DSL 的过滤器注入 SPI 实现，不属于数据源路由的一部分。

## 推荐继续阅读

- [实体绑定与优先级](entity-binding.md) —— 实体归属的声明方式与完整优先级链
- [配置方式](configuration.md) —— 连接定义、模块参数与刷新操作
- [Fluent DSL 指引](../orm/fluent-dsl.md) —— `useDataSource` 所在的查询 DSL 全貌
