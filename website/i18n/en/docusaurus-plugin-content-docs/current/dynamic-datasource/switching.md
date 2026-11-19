---
title: Datasource Switching
sidebar_label: Switching
---

# Datasource Switching

This page covers every way to switch datasources: which execution entries can switch at all, how `Dao` calls select a datasource automatically, the manual switching APIs, and **the limits of switching inside a transaction** — the boundary you should understand before using dynamic datasources.

Switching falls into two categories:

- **Automatic**: the entity has a declared owner (see [Entity Binding](entity-binding.md)) and the aspect routes automatically, invisibly to business code
- **Manual**: the caller explicitly specifies a datasource, overriding the entity declaration

Both rely on the same underlying mechanism: at execution time the routing datasource `DynamicRoutingDataSource` reads the datasource key from the thread context (`DynamicDataSourceHolder`, a `ThreadLocal<String>`) and picks the corresponding physical datasource. When the key's datasource has not been created yet, lazy pool creation is triggered.

## Precondition: the Entry Must Be `dynamicDao`

Switching capability depends on which datasource the Dao is bound to — this is the most common cause of "why didn't it switch":

| Dao bean | Datasource | Can switch? |
|---|---|---|
| `primaryDao` | direct physical primary | **never routes** — no key has any effect on it |
| `dynamicDao` | `DynamicRoutingDataSource` | the only Dao that consumes routing keys |

The ORM runtime's Dao binding rules (`OrmDaoResolver`, high → low):

1. explicit `geelato.orm.dao-bean-name` configuration
2. `dynamicDao` (bound preferentially whenever present — otherwise switching would silently not work)
3. `primaryDao`
4. the single Dao bean (multiple beans without standard names raise an error asking for explicit configuration)

In a typical project (using the Starter with `spring.datasource.primary.jdbc-url` configured) `dynamicDao` always exists and the ORM binds it automatically. **But if your code bypasses the ORM and injects `primaryDao` directly, switching never applies to those calls** — inject `dynamicDao` instead.

For field injection, pair it with the `@UseDynamicDataSource` annotation:

```java
public class OrderService {
    @UseDynamicDataSource
    private Dao dynamicDao; // resolved by "dynamic" + simple field type name
}
```

## Automatic Switching: How a `Dao` Call Selects

The `DataSourceInterceptor` aspect intercepts all `Dao` methods (`cn.geelato.core.orm.Dao.*(..)`) and selects before execution:

1. **recognize the entity**: inspect arguments in order to resolve an entity name
2. **resolve the datasource key**: adjudicate per the priority chain (see [Entity Binding](entity-binding.md))
3. **set the thread-context key**, then run the method
4. **restore the outer key in finally** — protecting nested calls

Entity recognition in step 1 (in argument order; unrecognized arguments are skipped):

| Argument shape | Recognition | Typical methods |
|---|---|---|
| `BoundPageSql` / `BoundSql` | the entityName carried by the command | paged queries, MQL execution |
| a `Class` annotated `@Entity` | the annotation name (or the simple class name when blank) | `querys(clazz, ...)` etc. |
| a non-empty `List` | recurse on the first element with the same rules | `batchSave` / `multiSave` / `multiDelete` |
| an instance annotated `@Entity` | same as above | `insert` / `save` / `update` |

When nothing is recognized, the entity name is empty and the fallback chain applies directly (annotation-scoped default > outer explicit key > request-level override > platform default > primary).

With an entity name resolved, the key is resolved per the [priority chain in Entity Binding](entity-binding.md); if the resolved key's datasource is not registered, resolution fails hard (an `IllegalStateException` is thrown, refusing silent fallback to primary) — **it never routes to a nonexistent datasource, nor quietly lands on a different one**.

## Manual Switching: Three APIs

Business code writes the thread-context key exclusively through `cn.geelato.datasource.DynamicDataSourceScope` (provided by geelato-orm) — the **single entry point** for datasource switching. `DynamicDataSourceHolder` (a `ThreadLocal<String>`) is only its underlying carrier; business code should not read or write it directly.

### 1. Block scope: `DynamicDataSourceScope.withDataSource`

Effective inside the block, restored on exit — ideal for temporary, code-level switching:

```java
List<Map<String, Object>> rows = DynamicDataSourceScope.withDataSource("biz_db", () ->
        dao.querys(SomeEntity.class));
```

- the pre-call value is restored whether the block ends normally or exceptionally (finally semantics built in), no manual cleanup
- framework paths — the MQL executor, the `DataSourceInterceptor` aspect, async tasks — all follow this semantic
- a blank connectId leaves the current key untouched and simply executes

### 2. Request-scoped default: `DynamicDataSourceScope.switchTo`

Stays in effect until the next switch or the end of the request — ideal for switching an entire request at its entry point:

```java
// inside a Controller / Service method: subsequent calls on this request thread that do not hit an entity mapping go to biz_db
DynamicDataSourceScope.switchTo("biz_db");
```

- a blank argument throws `IllegalArgumentException` immediately — never silently ignored
- it sets a thread-level key that applies to all subsequent calls that **do not hit an entity mapping**; calls that do hit an entity mapping still follow the entity's declared datasource (entity mapping has higher priority, see [Entity Binding](entity-binding.md))
- at the end of the request, the web-layer cleanup safety net reclaims it: `DataSourceContextCleanupFilter` (order=-100, outermost in the filter chain) clears both the thread key and the request-level override context in a finally block, so thread-pool reuse never carries the key into the next request
- the `switchDbByConnectId("biz_db")` retained on the platform `BaseController` / `BaseService` is a thin delegate of this method with identical semantics

### 3. Fluent DSL: `useDataSource(...)`

Specify the datasource on the ORM fluent API — **effective for this call only**, restored automatically afterwards, no manual cleanup:

```java
// query
List<Map<String, Object>> orders = MetaFactory.query("demo_order")
        .useDataSource("biz_db")
        .list();

// insert (entity-instance entry works the same: MetaFactory.insert(entity).useDataSource("biz").save())
MetaFactory.insert(DemoOrder.class)
        .useDataSource("biz_db")
        .save(order);
```

Override relationship at the DSL level: **explicit `useDataSource(...)` > the entity's connectId declaration > request-level override > platform default**. Even when the entity is bound to a database, a single call can temporarily point elsewhere with `useDataSource`.

## Nested Call Semantics

Four layers of protection keep nested switching from polluting each other:

- **interceptor finally-restore**: after an inner `Dao` call finishes, the thread-context key is restored to its pre-call value; keys set by the outer scope are unaffected
- **block scope**: `DynamicDataSourceScope.withDataSource` is effective inside the block and restores the previous value whether the block ends normally or exceptionally — the executor-side DSL `useDataSource` is implemented through it too (set → execute → restore in finally)
- **annotation defaults cleared eagerly**: the scoped default of a class/method-level `@UseDynamicDataSource` is cleared when the method ends and never leaks into the next request on a reused thread
- **cleanup safety net**: the request-scoped key set by `switchTo` and the request-level override context are reclaimed at the end of the request by `DataSourceContextCleanupFilter` (order=-100) — no reliance on caller discipline

So the semantics of a typical nested scenario are:

```java
DynamicDataSourceScope.switchTo("biz_db"); // outer scope switches to biz_db (equivalent to switchDbByConnectId)
dao.querys(SomeEntity.class, ...);         // SomeEntity has no declared owner → stays on biz_db (fallback level 3)
dao.querys(OtherEntity.class, ...);        // OtherEntity declares connectId="order_db" → routed to order_db (entity mapping wins)
dao.querys(ThirdEntity.class, ...);        // ThirdEntity has no declared owner → back to biz_db (inner call restored the outer key)
```

## Request-Level Override: Online Archive Querying

Besides the code-level switching above, there is a **request-level** default-source override channel: with the request header `X-Gl-Archive-Query: true`, all read operations on entities **without an explicit owner** within that request automatically route to the archive database. Underneath, geelato-orm provides the neutral request-level datasource override capability (`DataSourceOverrideContext`), and geelato-web-platform carries the archive semantics and the request filter. Key points:

- **only owner-less entities are switched**: entities with neither a `connectId` nor a mapped catalog; explicitly owned entities (users / dictionaries / metadata, etc.) keep their routing so the page framework works as usual
- **no explicit designation is ever hijacked**: entity ownership, annotations, the DSL `useDataSource`, and `withDataSource` blocks all take precedence over the request-level override
- **read-only**: write operations on owner-less entities in archive mode fail hard
- **hard failure**: when the archive datasource is not configured, requests carrying the header fail with an explicit error instead of silently degrading

See [Online Archive Querying](../archive/overview.md) for the full configuration, constraints and frontend contract.

## Switching Inside a Transaction: Limits (Important)

**Once a transaction begins, the datasource binding is fixed; changing the key inside the transaction has no effect.**

The reason: `DataSourceTransactionManager` borrows the **first physical connection** from the routing datasource when the transaction begins (`doBegin`) and binds it to the transaction. Afterwards, changing the thread-context key still leaves in-transaction operations on that already-bound connection.

Concrete symptoms:

- inside a Spring `@Transactional` method, operating on database A then switching to B: the B operations actually still land on A's connection
- the ORM's `batchSave(transaction = true)`: **the first command's connectId decides the transaction's database for the whole batch**; other commands' connectIds do not change the transaction's ownership

Practical advice:

- **do not wrap cross-database operations in one transaction** — split transaction boundaries by database, or accept an eventual-consistency approach
- within one transaction, keep all operations' entity ownership (or explicitly specified connectId) consistent
- when cross-database strong consistency is truly required, evaluate the reserved JTA / Seata capabilities (disabled by default, see the overview)

## Multi-Tenant Scenarios

Switching itself goes through the mechanisms above (entity binding / manual switching) and is tenant-agnostic. A common tenant isolation approach — "after switching to a tenant datasource, automatically append tenant filters" — is implemented via the Fluent DSL's filter-injection SPI and is not part of datasource routing.

## Suggested Reading

- [Binding & Priority](entity-binding.md) — how entity ownership is declared and the full priority chain
- [Configuration](configuration.md) — connection definitions, configuration properties and refresh operations
- [Fluent DSL Guide](../orm/fluent-dsl.md) — the query DSL that `useDataSource` belongs to
