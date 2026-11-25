---
title: Dynamic Datasource
sidebar_label: Dynamic Datasource
---

# Dynamic Datasource

This page explains the dynamic datasource capability provided by geelato-orm (the `cn.geelato.datasource` package), including how the runtime handles:

- datasource definition loading
- datasource instance construction
- datasource routing
- entity-to-datasource resolution
- reserved transaction-related capabilities

This page is the overview; the other pages expand by topic:

- [Loading & Registration](definition-loading.md) — where datasources come from, which class they are registered into, and how an application implements custom loading
- [Binding & Priority](entity-binding.md) — how entities bind to datasources and the full priority chain
- [Configuration](configuration.md) — the connection-definition contract, configuration properties and the default datasource
- [Switching](switching.md) — automatic/manual switching, the request-level override, nested semantics and transaction limits
- [Host Mapping](host-mapping.md) — local mapping of intranet addresses during development

For deeper ORM extension (`Dao` binding, the query-filter SPIs and so on), see [ORM / Datasource Extension](../orm/datasource-extension.md).

## Capability Position

The dynamic datasource capability is not just a standalone connection-pool helper. It is the component inside geelato-orm that provides dynamic routing around the ORM and `Dao` execution chain.

Its responsibility can be summarized as:

- letting `dynamicDao` and `dynamicJdbcTemplate` switch connections by entity or context
- converting datasource definitions into real `DataSource` instances
- allowing host applications to adopt multiple datasources without changing the `Dao` calling style

So business code focuses more on "which datasource should this entity use" than on manually managing pool instances.

## Core Beans Created

The wiring entry of the dynamic datasource stack is `OrmDynamicDataSourceAutoConfiguration`: when a bean named `primaryJdbcTemplate` exists, it imports the registry `DynamicDataSourceRegistry`, the factory `DataSourceFactory`, the routing datasource `DynamicRoutingDataSource`, the entity ownership resolver `EntityDataSourceResolver` and the aspect `DataSourceInterceptor`.

Among them:

- `dynamicDataSource` is the real routing datasource
- `dynamicJdbcTemplate` is a `JdbcTemplate` built on top of it
- `DynamicDataSourceRegistry` is the registry that keeps datasource definitions and pools

The definition loader `DynamicDataSourceDefinitionLoader` is not wired here. The host application provides it: the framework module `geelato-orm` ships no implementation, while a platform host ships `PlatformDynamicDataSourceDefinitionLoader`. See [Loading & Registration](definition-loading.md) for the full picture.

## Overall Flow

The runtime flow can be understood as:

1. at startup `DynamicDataSourceRegistry` calls the definition loader's `loadAll()` while it is being constructed
2. the registry keeps datasource configs and builds `DataSource` instances on demand
3. `DataSourceInterceptor` resolves the entity owner before `Dao` execution; `DynamicDataSourceScope` (the single switching entry point) writes the datasource key into the thread context
4. `DynamicRoutingDataSource` selects the real datasource by the current thread key
5. at the end of the request, `DataSourceContextCleanupFilter` clears the thread context (the request-level override context is reclaimed as well)

So the core chain is a combination of:

- metadata resolution
- ThreadLocal-based selection (single entry point `DynamicDataSourceScope`)
- routing-datasource execution

## Where Definitions Come From

Datasources have three sources:

- the primary and secondary pools from Spring configuration: `primary` / `secondary`, which bypass the registry
- the definition loader bean `DynamicDataSourceDefinitionLoader`: `loadAll()` is called once when the registry is constructed, and the `id` of each definition Map is the routing key
- a direct `DynamicDataSourceRegistry.registerDataSource(...)` call at runtime, which registers an already-built pool

Two rules about the definition loader decide how a host application plugs in:

- exactly **one** loader is picked up by type. A host application customizes the datasource source by providing its own implementation; a platform host already has one, so `@Primary` is required for the new one to take effect.
- with no implementation at all, the framework only uses `primary` / `secondary` and still runs standalone.

The implementation a platform host ships is `PlatformDynamicDataSourceDefinitionLoader` (in the business layer `geelato-web-platform`), which loads from the connection definitions maintained by the platform.

The startup sequence, the registry methods, the field contract and custom examples are collected in one page: [Loading & Registration](definition-loading.md).

## How Datasource Instances Are Built

The class that turns a datasource definition into a real `DataSource` is:

- `DataSourceFactory`

It currently supports:

- MySQL
- PostgreSQL

and uses:

- `HikariDataSource`

as the default pool implementation.

It also handles:

- password decryption
- host remapping through `DbHostMapFileLoader`
- unified pool settings
- optional Seata proxy wrapping

So this is not only JDBC URL assembly. It centralizes:

- address resolution
- driver selection
- pool tuning
- secure password handling
- host mapping

in one factory.

## How Routing Happens

The routing core is:

- `DynamicRoutingDataSource`

which extends:

- `AbstractRoutingDataSource`

The current lookup key comes from:

- `DynamicDataSourceHolder` (essentially a `ThreadLocal<String>`)

and every write to it converges on the single entry point:

- `DynamicDataSourceScope` — `withDataSource(...)` block scope / `switchTo(...)` request-scoped default

So the current routing model is:

- write a datasource key into the current thread
- let the routing datasource read that key at execution time
- resolve the real `DataSource`

If the key exists but the datasource has not been physically created yet, `DynamicRoutingDataSource` will:

- create it lazily
- add it into the internal routing map
- refresh target datasource mappings

This is the foundation of the current lazy-loading behavior.

## Why Primary Always Exists

When the routing map is refreshed, `DynamicRoutingDataSource` requires:

- `primaryDataSource` must not be `null`

and uses it as:

- the default datasource

If `secondaryDataSource` exists, it is also added into the routing map.

So the current design is:

- `primary` is the mandatory fallback datasource
- `secondary` is an optional fixed datasource
- other dynamic datasources extend on top of those two

## How an Entity Maps to a Datasource

The component that resolves datasource by entity is:

- `EntityDataSourceResolver`

Its current logic is:

1. check the local entity-to-datasource cache
2. if needed, resolve from entity metadata
3. read `connectId` from table metadata
4. verify the key is registered in `DynamicDataSourceRegistry` — an unregistered key fails hard, refusing silent fallback to primary

So the key mapping is:

- `EntityMeta.tableMeta.connectId -> dynamic datasource key`

This means once an entity is bound with `connectId`, ORM execution can route to the matching datasource automatically.

Besides reading `connectId` from database metadata, an entity can also declare its datasource via the `@Entity` annotation, adjudicated uniformly by `MetaManager.resolveConnectId`. The resolution rules (high → low) are:

1. `TableMeta.connectId` is non-blank — note that an explicit `@Entity(connectId)` and a runtime-registered value (the registration entry is provided by the platform) write into the **same slot** (runtime registration merges later and can overwrite the annotation value for non-platform entities); they are not two priority levels
2. the mapping of `@Entity(catalog)` in `catalog-mapping`
3. neither → null, handed over to the fallback chain at Dao-call time

In other words, even without a registered `connectId` in the platform table, routing works through `@Entity(connectId=...)` alone or `@Entity(catalog=...)` plus `catalog-mapping`. See [Binding & Priority](entity-binding.md) for the dedicated coverage of binding styles and the full priority chain.

It also provides manual operations such as:

- `addEntityMapping(...)`
- `removeEntityMapping(...)`
- `clearCache()`

which are useful for runtime cache correction or local overrides.

## How `Dao` Calls Automatically Switch Datasource

The aspect responsible for setting the current datasource key is:

- `DataSourceInterceptor`

It handles two main things.

### 1. `@UseDynamicDataSource`

If a class or method is annotated with:

- `@UseDynamicDataSource`

the annotation `value()` becomes the current default datasource.

This is suitable when:

- a whole service class should default to one datasource
- one method explicitly wants a default routing target

### 2. Intercepting `Dao.*`

The aspect wraps:

- `cn.geelato.core.orm.Dao.*(..)`

and tries to extract entity information from the arguments in order (unrecognized arguments are skipped):

- `BoundPageSql` / `BoundSql` (the entityName carried by the command)
- entity classes annotated with `@Entity`
- non-empty `List` (batchSave / multiSave / multiDelete — the first element is recognized with the same rules)
- entity instances annotated with `@Entity` (insert / save / update)

Once the entity name is resolved, it calls:

- `EntityDataSourceResolver.resolveDataSource(entityName)`

If resolution succeeds, it switches to the entity datasource; if no ownership is resolved, the fallback chain applies, and the outer key is restored after the call (protecting nested switching semantics).

So the current priority is (high → low):

- entity mapping (`TableMeta.connectId`, annotation and DB registration share one slot → `@Entity(catalog)` mapping)
- the class/method-level `@UseDynamicDataSource` default
- the outer explicit key (thread-context value set by `DynamicDataSourceScope.switchTo` / `withDataSource`)
- the request-level override (only for entities without an explicit owner, driven e.g. by the archive-query header)
- the platform default key (written into `DataSourceManager` at startup by `OrmAutoConfiguration`; it holds the dialect snapshot and the default key, not the datasource registry — see [Loading & Registration](definition-loading.md))
- the `primary` hard fallback

See [Switching](switching.md) for the dedicated coverage of switching styles and transaction limits.

## Lazy Loading and Refresh

The registry `DynamicDataSourceRegistry` keeps two maps:

- definition snapshots in `dataSourceConfigMap`
- built pools in `dataSourceMap`

Lazy loading is on by default (`geelato.datasource.dynamic.delay-load-data-source=true`): startup stores definitions only and creates no pools; the `DataSource` for a key is built the first time a call is routed to it. Setting it to `false` builds the pool when the definition is registered.

This is more suitable when there are many configured datasources but only a small subset is active at the same time.

For every registry method and the two-step refresh and removal operations, see [Loading & Registration](definition-loading.md).

## Pool and Runtime Properties

The unified configuration prefix is:

```properties
geelato.datasource.dynamic.*
```

Key properties include:

- `delay-load-data-source`
- `enable-jta-transaction`
- `enable-seata-proxy`
- `minimum-idle`
- `maximum-pool-size`
- `idle-timeout-ms`
- `max-lifetime-ms`
- `connection-timeout-ms`
- `validation-timeout-ms`
- `keepalive-time-ms`
- `initialization-fail-timeout-ms`
- `connection-test-query`
- `connect-timeout-ms`
- `socket-timeout-ms`

The default direction is:

- keep ordinary business applications lightweight
- keep heavy transaction frameworks disabled by default
- centralize pool settings in one place

## Current JTA / Seata Status

The implementation reserves extension points for:

- JTA
- Seata

and related configuration and transaction classes already exist, but the default strategy is still:

- do not enable them automatically

Important details:

- `TransactionalAspect` is currently not actively intercepting method calls
- Seata proxy support is reserved as a compatibility entry
- JTA and distributed transactions are optional enhancements, not part of the minimum default path

So for ordinary business applications, it is better to understand the current state as:

- extension points exist
- wiring positions exist
- but the default path still focuses on lightweight local routing

## Recommended Usage

The recommended mental model is:

- treat the primary datasource as the stable fallback
- leave the source of truth for connection definitions to the definition-source SPI (the platform provides the default implementation)
- treat `connectId` in entity metadata as the core entity-to-datasource binding
- treat `dynamicDao` as the preferred ORM execution entry for routed access
- do all manual switching through `DynamicDataSourceScope`: `withDataSource(...)` for block scope, `switchTo(...)` for a request-scoped default (the platform `switchDbByConnectId(...)` is a thin delegate)
- enable JTA or Seata only when there is a clear need

## When To Read the Extension Chapter

If you only want to:

- understand how the current dynamic datasource works
- use the routing capability as provided
- switch datasource automatically by entity

this page is enough.

If you want to do things such as:

- replacing `DynamicDataSourceDefinitionLoader` to load datasource definitions from a config center or files: see Loading & Registration
- overriding the default ORM `Dao` binding, or customizing a multi-`Dao` strategy: see ORM / Datasource Extension
- enabling JTA or Seata and integrating further: see ORM / Datasource Extension

then continue with:

- [Loading & Registration](definition-loading.md)
- [ORM / Datasource Extension](../orm/datasource-extension.md)

## Suggested Reading

- [Loading & Registration](definition-loading.md)
- [Binding & Priority](entity-binding.md)
- [Configuration](configuration.md)
- [Switching](switching.md)
- [ORM / Datasource Extension](../orm/datasource-extension.md)
- [ORM Overview](../orm/overview.md)
- [Fluent DSL Guide](../orm/fluent-dsl.md)
- [Materialized View Acceleration (Platform)](../platform-capabilities/materialized-view.md) — slow queries served by engine-maintained materialized views (platform capability)
