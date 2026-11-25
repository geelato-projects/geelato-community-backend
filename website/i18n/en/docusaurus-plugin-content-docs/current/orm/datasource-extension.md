---
title: ORM / Datasource Extension
sidebar_label: ORM / Datasource Extension
---

# ORM / Datasource Extension

This page covers the ORM's datasource binding, runtime datasource switching, and the SPI extension entries for query filtering and field filling — three common tasks:

- Getting the ORM bound to the right `Dao`.
- Switching datasources at runtime.
- Extending query filtering and field-filling rules through SPIs.

To understand how the dynamic datasource capability itself works first, read [Dynamic Datasource](../dynamic-datasource/overview.md). For where datasources come from and how to implement custom loading, see [Definition Loading and Registration](../dynamic-datasource/definition-loading.md).

## Clarify Your Scenario

Before changing any configuration, identify the scenario so you don't reach for the wrong entry point:

- Route a single Fluent DSL query to a specific datasource: use `.useDataSource(...)`.
- Switch a block of code or an entire request to a dynamic datasource: use `DynamicDataSourceScope.withDataSource(...)` / `switchTo(...)`.
- Switch a whole service or method to a dynamic datasource: use `@UseDynamicDataSource`.
- Replace the dynamic datasource definition source: implement `DynamicDataSourceDefinitionLoader`, see [Definition Loading and Registration](../dynamic-datasource/definition-loading.md).
- Automatically add tenant/permission conditions to queries, or fill default fields on save: implement the corresponding SPI.

These capabilities are related but not interchangeable; picking the wrong entry point leads to configuration confusion.

## Current ORM Entry

The current ORM auto-configuration entry is `OrmAutoConfiguration`, with two core concerns:

- creating `MetaCommandExecutor`
- creating `SaveDefaultValueFiller`

`MetaCommandExecutor` requires a `Dao` bean in the host application.

## Shortest Integration Path

When integrating a new host application, work in this order.

### Step 1: Make Sure a `Dao` Exists

Minimal example:

```java
@Configuration
public class OrmDaoConfiguration {
    @Bean
    public Dao primaryDao(JdbcTemplate jdbcTemplate) {
        return new Dao(jdbcTemplate);
    }
}
```

Without a `Dao`, the ORM's main execution chain has no stable landing point.

### Step 2: Confirm Which `Dao` the ORM Binds

The ORM auto-resolution binds `dynamicDao` preferentially (the only Dao that supports `.useDataSource(...)` switching); it falls back to `primaryDao`, then to the single Dao bean — no explicit configuration needed in multi-`Dao` scenarios. Only configure a property when you need to bind some other custom `Dao`:

```properties
geelato.orm.dao-bean-name=myDao
```

### Step 3: Decide Whether You Need the Dynamic Datasource

Not every project needs it.

Continue only when you have one of these requirements:

- one set of service code needs to access multiple databases
- certain queries/stored procedures must switch to other databases by key
- the host application needs to manage the dynamic database list itself

## Which Dao Does the ORM Bind

Without explicit configuration, the ORM's auto-resolution order is:

1. `dynamicDao` (built on the routing datasource, the only Dao that supports `.useDataSource(...)` / entity `connectId` switching)
2. `primaryDao`
3. the single Dao bean

That is: as long as `dynamicDao` exists in the container, the ORM binds it — no explicit configuration needed in multi-`Dao` scenarios. If the host application sets `geelato.orm.dao-bean-name`, the explicit configuration wins and is used to bind some other custom `Dao` bean.

## Starter JDBC Defaults

When `spring.datasource.primary.jdbc-url` exists, the starter creates:

- `primaryDataSource`
- `primaryJdbcTemplate`
- `primaryDao`
- `dbGenerateDao`

If `spring.datasource.secondary.jdbc-url` is also configured, it additionally creates:

- `secondaryDataSource`
- `secondaryJdbcTemplate`
- `secondaryDao`

## Dynamic Datasource Extension Points

The real extension point of the dynamic datasource is the definition loader `DynamicDataSourceDefinitionLoader`, through which the host application supplies datasource definitions; see [Definition Loading and Registration](../dynamic-datasource/definition-loading.md).

The explicit configuration prefix is:

```properties
geelato.datasource.dynamic.*
```

## Minimal Enablement (Host Application)

The dynamic datasource beans follow these naming conventions (useful for troubleshooting and overriding):

- prerequisite: `primaryJdbcTemplate`
- auto-configured dynamic datasource beans:
  - `dynamicDataSource`
  - `dynamicJdbcTemplate`
  - `dynamicDao`

In a typical project the Starter provides `primaryJdbcTemplate`; without the Starter, the business application can provide a `primaryJdbcTemplate` itself (e.g. a `JdbcTemplate` built on Spring Boot's primary datasource) to trigger the dynamic datasource auto-configuration.

Key defaults include:

- `delay-load-data-source=true`
- `enable-jta-transaction=false`
- `enable-seata-proxy=false`
- default pool settings and `connection-test-query=SELECT 1`

## How to Switch Datasources

This is the most common part. Start with the lightest usage.

### Style 1: Explicit Switching in the Fluent DSL

This fits "route just one ORM query or procedure call to a specific datasource".

Query example:

```java
List<Map<String, Object>> rows = MetaFactory.query("DevDbConnect")
        .useDataSource("portal")
        .page(1, 10)
        .list();
```

Stored procedure example:

```java
List<Map<String, Object>> rows = MetaFactory.procedure("proc_query_user_orders")
        .in("userId", "U1001")
        .useDataSource("portal")
        .list();
```

Passthrough SQL example:

```java
List<Map<String, Object>> rows = MetaFactory.sql("select id, name from platform_user where del_status = ?")
        .param(0)
        .useDataSource("portal")
        .list();
```

If your need is only "switch this one chain", prefer this style — the most direct.

### Style 2: `@UseDynamicDataSource` on Classes, Methods or Fields

`@UseDynamicDataSource` fits more component-level scenarios.

It can be placed on:

- a class
- a method
- a field

The simplest class-level example:

```java
@Service
@UseDynamicDataSource("portal")
public class PortalUserService {
}
```

Method-level example:

```java
@Service
public class PortalUserService {

    @UseDynamicDataSource("portal")
    public void syncUsers() {
    }
}
```

It also supports entity-to-datasource mappings:

```java
@UseDynamicDataSource(
        value = "primary",
        mappings = {
                @UseDynamicDataSource.EntitySourceMapping(entityName = "Order", dataSource = "portal"),
                @UseDynamicDataSource.EntitySourceMapping(entityName = "Customer", dataSource = "crm")
        }
)
public class SyncService {
}
```

If a group of methods should share one datasource, or you need to wire the `dynamicDao` stack, `@UseDynamicDataSource` is more concise than writing `.useDataSource(...)` on every query.

### Style 3: Code-Level Switching with `DynamicDataSourceScope`

When you need "this block of code / this entire request on another database", use the single switching entry point `cn.geelato.datasource.DynamicDataSourceScope` (business code should not read or write the underlying `DynamicDataSourceHolder` directly):

```java
// block scope: effective inside the block, restored on normal or exceptional exit
List<Map<String, Object>> rows = DynamicDataSourceScope.withDataSource("portal", () ->
        dao.querys(SomeEntity.class));

// request-scoped default: stays in effect until the next switch or the end of the request,
// reclaimed by the cleanup safety net
DynamicDataSourceScope.switchTo("portal");
```

The `switchDbByConnectId(...)` retained on the platform `BaseController` / `BaseService` is a thin delegate of `switchTo` — call it directly in Controller scenarios. For nested semantics, the cleanup safety net and the in-transaction limits, see [Switching](../dynamic-datasource/switching.md).

### Style 4: Let the ORM Bind the Dynamic-Source `Dao` by Default

If most ORM operations in the application should land on the dynamic-source chain, no configuration is needed: as long as `dynamicDao` exists in the container the ORM binds it automatically, and the Fluent DSL gets the dynamic-source capability by default (`useDataSource` switching works) without per-call designation.

### Style 5: Declare Entity Ownership with the `@Entity` Annotation

The previous styles decide the datasource "at call time", "inside a code block", or "at component level". To make an entity's datasource ownership **fixed and written into the entity declaration**, declare it with the `@Entity` annotation.

This fits:

- an entity that inherently belongs to another database (e.g. orders fixed in the order database)
- a batch of entities grouped by module onto different databases (e.g. an `email` group on the mail database)
- avoiding repeated datasource designation on every service or query

This style sits at a different level from the previous ones and can be combined: the annotation declares the entity's "default ownership", while `.useDataSource(...)` can still override it on a single call.

#### Path A: Explicit `connectId`

Declare the datasource directly on the entity — the highest priority:

```java
@Entity(name = "demo_order", table = "t_demo_order", connectId = "order_db")
public class DemoOrder {
    // ...
}
```

From then on, every ORM operation on this entity routes to `order_db` automatically; no per-call switching needed.

#### Path B: `catalog` Grouping + Configuration Mapping

If a batch of entities belongs to the same database, tagging each with `connectId` is tedious. Tag them with the same `catalog` instead, then map the `catalog` to a datasource via configuration:

```java
// a group of entities all tagged with the same catalog
@Entity(name = "demo_order", catalog = "business")
public class DemoOrder { }

@Entity(name = "demo_order_item", catalog = "business")
public class DemoOrderItem { }
```

Then map the `catalog` to a datasource in configuration:

```properties
geelato.datasource.dynamic.catalog-mapping.platform=primary
geelato.datasource.dynamic.catalog-mapping.business=order_db
```

Equivalent YAML:

```yaml
geelato:
  datasource:
    dynamic:
      catalog-mapping:
        platform: primary
        business: order_db
```

The benefit: switching databases means changing configuration only, not code. All entities in the `business` group move to the new datasource together.

#### Resolution Priority

Which datasource an entity finally uses is determined in this order (high → low):

1. `TableMeta.connectId` is non-blank — an explicit `@Entity(connectId)` and a runtime-registered value (the registration entry is provided by the platform) write into the **same slot** (runtime registration merges later and can overwrite the annotation value for non-platform entities); they are not two priority levels
2. the mapping of `@Entity(catalog)` in `catalog-mapping`
3. neither → handed over to the Dao-call-time fallback chain (annotation default > outer explicit key > request-level override > platform default > `primary`)

Resolution happens on demand at runtime via `MetaManager.resolveConnectId`, so it does not matter when the configuration is injected. See [Binding & Priority](../dynamic-datasource/entity-binding.md) for the full priority chain and decision flow.

#### Notes

- The datasource key a mapping points to must be a registered dynamic datasource (the key has been provided by the definition source, or supplied through a custom loader or another registration channel); when unregistered, the first resolution fails hard (an `IllegalStateException` is thrown, refusing silent fallback to primary).
- System entities with `catalog = "platform"` keep their existing protection (Java class definition always wins, refresh and hand-over forbidden); mapping `platform` to `primary` in `catalog-mapping` is recommended to stay consistent with the system database.
- Resolution completes on demand at query time, independent of entity scan order — no need to worry about framework startup sequencing.

## Datasource Definition Source: Loading and Registration

"Where datasource definitions come from" is the job of the SPI `DynamicDataSourceDefinitionLoader` (in the `cn.geelato.datasource.spi` package of `geelato-orm`), and definitions are registered in `DynamicDataSourceRegistry`. Because the source is an SPI, connection details can come from a config center, files or an external service, and a host application does not need an extra table structure just to use multiple datasources. The framework module ships no implementation; with no implementation at all, only `primary` / `secondary` are used.

Three points matter when customizing the datasource source in a host application:

- The registry picks up exactly **one** loader by type. When the container already has one (a platform host ships `PlatformDynamicDataSourceDefinitionLoader`), the custom implementation must be marked `@Primary`, otherwise startup fails with `NoUniqueBeanDefinitionException`.
- Marking it `@Primary` is a complete replacement: connections from the existing source are no longer registered. To keep the existing source and add your own, write a merged loader.
- Refreshing after a definition change has two steps: refresh `DynamicDataSourceRegistry` first, then `DynamicRoutingDataSource`.

The interface contract, the registry methods, the startup sequence, complete examples and runtime refresh are all in [Definition Loading and Registration](../dynamic-datasource/definition-loading.md).

## Implementing the SPIs

Datasource extensions often appear together with platform rule extensions. For example:

- after switching to a tenant datasource, tenant filters should be appended automatically
- certain save chains should fill tenant code, creator, update time, etc.

Don't hard-code these into business services — implement the SPIs instead.

### First Decide Which SPI to Implement

Choose by entry point:

- to affect Fluent DSL queries: implement `FluentQueryFilterInjector`
- to affect Fluent DSL saves: implement `FluentSaveFieldValueFiller`
- to affect MQL queries: implement `MqlQueryFilterInjector`
- to affect MQL saves: implement `MqlSaveFieldValueFiller`

### Example 1: Fluent DSL Query Filter SPI

```java
@Component
public class DemoFluentQueryFilterInjector implements FluentQueryFilterInjector {

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void inject(QueryCommand command, MetaQuery query) {
        // inject platform-level filter conditions into the Fluent DSL query command here
    }
}
```

### Example 2: Fluent DSL Save Field Fill SPI

```java
@Component
public class DemoFluentSaveFieldValueFiller implements FluentSaveFieldValueFiller {

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void fill(FluentSaveFieldValueFillContext context) {
        // fill default save fields here
    }
}
```

### The SPI Runtime Rules You Must Remember

Every SPI class follows the same key rules:

1. `0` implementations: skipped
2. `1` implementation: executed per `isEnabled()`
3. multiple implementations: exception thrown immediately

This means:

- do not enable two `FluentQueryFilterInjector`s at the same time
- do not enable two `FluentSaveFieldValueFiller`s at the same time
- with multiple candidates in the project, converge them into a single enabled implementation upstream

For a more complete SPI description, see [Query Filter and Field Fill SPI](../reference/spi-query-filter-and-save-fill-extension.md).

## When JTA / Seata Are Enabled

Neither JTA nor Seata is enabled by default.

They only enter their capability chains after explicit configuration, for example:

```properties
geelato.datasource.dynamic.enable-jta-transaction=true
```

So the minimal skeleton and ordinary business applications stay free of heavy-transaction dependencies by default.

## Recommended Order of Operations

When a new project needs ORM, dynamic datasources and platform rules at once, the recommended order is:

1. first verify `primaryDao` or another basic `Dao` works
2. then confirm the ORM auto-bound `dynamicDao` (bound whenever present; switching does not work when `primaryDao` is bound)
3. then enable dynamic datasources and verify `.useDataSource(...)`
4. then add `@UseDynamicDataSource` as needed
5. finally implement the query filter and field fill SPIs

The benefit: when something breaks, you can quickly locate whether it is

- the `Dao` not wired correctly
- the dynamic source not taking effect
- the datasource definition not loaded
- an SPI extension written incorrectly

## Troubleshooting Step by Step

When "switching doesn't take effect" or "the SPI doesn't fire", check in this order:

1. whether `primaryDao` / `dynamicDao` really exist in the container
2. whether the ORM bound `dynamicDao` (it should be auto-bound when `geelato.orm.dao-bean-name` is not configured)
3. whether the dynamic source definition really loads the target key
4. whether the code really calls `.useDataSource("...")` or hits `@UseDynamicDataSource`
5. whether more than one implementation of the same SPI is registered
6. whether the SPI's `isEnabled()` returns `true`
7. a custom loader fails at startup with `NoUniqueBeanDefinitionException` → the implementation is missing `@Primary`
8. `UnsupportedOperationException: unsupported database type` → the Map's `db_type` is neither the built-in `mysql` / `postgresql` nor a dialect family registered via `Dialects.registerAlias`
9. the source changed but switching doesn't take effect → only `DynamicDataSourceRegistry` was refreshed, not `DynamicRoutingDataSource` (both steps are required)

The full troubleshooting for definition loaders is in [Definition Loading and Registration](../dynamic-datasource/definition-loading.md).

## Practical Recommendations

- prefer the ORM for regular backend CRUD
- override the definition source through the extension point instead of hard-modifying the default implementation
- no configuration needed in multi-`Dao` scenarios — the ORM auto-binds `dynamicDao`; configure `geelato.orm.dao-bean-name` only to bind some other custom `Dao`
- do not enable JTA / Seata by default unless clearly needed
- prefer `.useDataSource(...)` for single-chain source switching
- consider `@UseDynamicDataSource` for component-level switching
- use `DynamicDataSourceScope.withDataSource(...)` / `switchTo(...)` for block / request-scoped switching; do not read or write `DynamicDataSourceHolder` directly
- when an entity's datasource ownership is fixed, declare it with `@Entity(connectId/catalog)` instead of switching per call
- prefer SPIs for platform-level query and field rules rather than scattering them across business code

## Suggested Reading

- [Dynamic Datasource](../dynamic-datasource/overview.md)
- [Definition Loading and Registration](../dynamic-datasource/definition-loading.md)
- [ORM Overview](overview.md)
- [MetaStore Extension](../reference/metastore-extension.md)
- [Query Filter and Field Fill SPI](../reference/spi-query-filter-and-save-fill-extension.md)
- [Minimal Integration](../guide/minimal-integration.md)
