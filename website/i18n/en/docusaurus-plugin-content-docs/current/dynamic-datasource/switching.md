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

When nothing is recognized, the entity name is empty and the fallback chain applies directly (annotation-scoped default > outer explicit key > platform default > primary).

With an entity name resolved, the key is resolved per the [priority chain in Entity Binding](entity-binding.md); if the resolved key's datasource is not registered, it is treated as unresolved and the fallback chain applies — **it never routes to a nonexistent datasource**.

## Manual Switching: Three APIs

### 1. Controller base class: `switchDbByConnectId`

A convenience method on the platform `BaseController`, setting the thread context directly:

```java
// inside a Controller method: subsequent Dao calls on this request thread go to biz_db
switchDbByConnectId("biz_db");
```

- a blank argument throws `IllegalArgumentException` immediately — never silently ignored
- it sets a thread-level key that applies to all subsequent calls that **do not hit an entity mapping**
- calls that do hit an entity mapping still follow the entity's declared datasource (entity mapping has higher priority, see [Entity Binding](entity-binding.md))

### 2. Thread context: `DynamicDataSourceHolder`

The low-level API — `switchDbByConnectId` delegates to it:

```java
DynamicDataSourceHolder.setDataSourceKey("biz_db");   // set
DynamicDataSourceHolder.getDataSourceKey();           // read (null when unset)
DynamicDataSourceHolder.clearDataSourceKey();         // clear
```

When using it directly, **always restore or clear in a finally block**, otherwise thread reuse (thread pools) carries the key into the next request. Prefer `switchDbByConnectId` (Controller scope) or the DSL below, both of which scope the key for you.

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

Override relationship at the DSL level: **explicit `useDataSource(...)` > the entity's connectId declaration > platform default**. Even when the entity is bound to a database, a single call can temporarily point elsewhere with `useDataSource`.

## Nested Call Semantics

Three layers of protection keep nested switching from polluting each other:

- **interceptor finally-restore**: after an inner `Dao` call finishes, the thread-context key is restored to its pre-call value; keys set by the outer scope are unaffected
- **DSL automatic scoping**: `useDataSource` is effective only for the duration of the command (executor-side `withDataSource`: set → execute → restore in finally)
- **annotation defaults cleared eagerly**: the scoped default of a class/method-level `@UseDynamicDataSource` is cleared when the method ends and never leaks into the next request on a reused thread

So the semantics of a typical nested scenario are:

```java
switchDbByConnectId("biz_db");          // outer scope switches to biz_db
dao.querys(SomeEntity.class, ...);      // SomeEntity has no declared owner → stays on biz_db (fallback level 3)
dao.querys(OtherEntity.class, ...);     // OtherEntity declares connectId="order_db" → routed to order_db (entity mapping wins)
dao.querys(ThirdEntity.class, ...);     // ThirdEntity has no declared owner → back to biz_db (inner call restored the outer key)
```

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
- [Configuration](configuration.md) — connection definitions, module properties and refresh operations
- [Fluent DSL Guide](../orm/fluent-dsl.md) — the query DSL that `useDataSource` belongs to
