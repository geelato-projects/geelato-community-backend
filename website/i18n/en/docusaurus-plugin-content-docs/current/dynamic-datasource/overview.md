# Dynamic Datasource

This page explains the current dynamic datasource capability provided by `geelato-dynamic-datasource`, including how the runtime handles:

- datasource definition loading
- datasource instance construction
- datasource routing
- entity-to-datasource resolution
- reserved transaction-related capabilities

If your goal is not just to understand the built-in behavior but to replace the default definition source or deeply customize the strategy, continue with:

- [ORM / Datasource Extension](../orm/datasource-extension.md)

This chapter has four pages; this one is the overview and the other three expand by topic:

- [Binding & Priority](entity-binding.md) — how entities bind to datasources and the full priority chain
- [Configuration](configuration.md) — connection definitions, module properties, the default datasource and refresh
- [Switching](switching.md) — automatic/manual switching, nested semantics and transaction limits

## Module Position

`geelato-dynamic-datasource` is not just a standalone connection-pool helper. It is the framework module that provides dynamic routing around the ORM and `Dao` execution chain.

Its responsibility can be summarized as:

- letting `dynamicDao` and `dynamicJdbcTemplate` switch connections by entity or context
- converting platform-managed datasource definitions into real `DataSource` instances
- allowing host applications to adopt multiple datasources without changing the `Dao` calling style

So business code focuses more on "which datasource should this entity use" than on manually managing pool instances.

## Core Beans Created

The configuration entry is:

- `DynamicDataSourceConfiguration`

It currently creates:

- `dynamicDataSource`
- `dynamicJdbcTemplate`
- `DynamicDataSourceDefinitionLoader`

Where:

- `dynamicDataSource` is the real routing datasource
- `dynamicJdbcTemplate` is a `JdbcTemplate` built on top of it
- `DynamicDataSourceDefinitionLoader` is the definition source abstraction

If the host application does not provide its own `DynamicDataSourceDefinitionLoader`, the default bean is:

- `PlatformDynamicDataSourceDefinitionLoader`

## Overall Flow

The runtime flow can be understood as:

1. `DynamicDataSourceRegistry` loads datasource definitions at startup
2. the registry keeps datasource configs and builds `DataSource` instances on demand
3. `DynamicRoutingDataSource` selects the real datasource by the current thread key
4. `DataSourceInterceptor` resolves the entity and sets the datasource key before `Dao` execution
5. ORM and `Dao` continue execution on the selected datasource

So the core chain is a combination of:

- metadata resolution
- ThreadLocal-based selection
- routing-datasource execution

## Where Definitions Come From

The current default definition loader is:

- `PlatformDynamicDataSourceDefinitionLoader`

It reads from the primary database table:

- `platform_dev_db_connect`

using:

- `SELECT * FROM platform_dev_db_connect`
- `SELECT * FROM platform_dev_db_connect WHERE id = ?`

This means the current default design is:

- dynamic datasource definitions are centrally managed in a platform table
- the routing key aligns with the connection definition primary key `id`

If you do not want the platform table to remain the source of truth, and instead want to use a configuration center, YAML, external service, or registry, do not modify the built-in implementation directly. Read:

- [ORM / Datasource Extension](../orm/datasource-extension.md)

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

- `DynamicDataSourceHolder`

and that holder is essentially:

- `ThreadLocal<String>`

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
4. confirm the key exists in `DynamicDataSourceRegistry`

So the key mapping is:

- `EntityMeta.tableMeta.connectId -> dynamic datasource key`

This means once an entity is bound with `connectId`, ORM execution can route to the matching datasource automatically.

Besides reading `connectId` from database metadata, an entity can also declare its datasource via the `@Entity` annotation, adjudicated uniformly by `MetaManager.resolveConnectId`. The resolution rules (high → low) are:

1. `TableMeta.connectId` is non-blank — note that an explicit `@Entity(connectId)` and a registered `platform_dev_table.connect_id` value write into the **same slot** (runtime registration merges later and can overwrite the annotation value for non-platform entities); they are not two priority levels
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

If resolution succeeds, it switches to the entity datasource; if not, the fallback chain applies, and the outer key is restored after the call (protecting nested switching semantics).

So the current priority is (high → low):

- entity mapping (`TableMeta.connectId`, annotation and DB registration share one slot → `@Entity(catalog)` mapping)
- the class/method-level `@UseDynamicDataSource` default
- the outer explicit key (thread-context value set by `switchDbByConnectId` / `useDataSource` etc.)
- the platform default key (written into `DataSourceManager` at startup by `OrmAutoConfiguration`)
- the `primary` hard fallback

See [Switching](switching.md) for the dedicated coverage of switching styles and transaction limits.

## Lazy Loading and Refresh

The registry is:

- `DynamicDataSourceRegistry`

It maintains:

- datasource instance cache `dataSourceMap`
- datasource config cache `dataSourceConfigMap`

It supports:

- full refresh at startup
- refresh of one datasource by key
- datasource removal
- direct registration of an already-built datasource

If:

```properties
geelato.datasource.dynamic.delay-load-data-source=true
```

is enabled, startup loads only definitions and not every physical pool instance. The real `DataSource` is created only when the route first needs that key.

This is more suitable when there are many configured datasources but only a small subset is active at the same time.

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

The default direction is:

- keep ordinary business applications lightweight
- keep heavy transaction frameworks disabled by default
- centralize pool settings in one place

## Current JTA / Seata Status

The module does reserve extension points for:

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
- treat `platform_dev_db_connect` as the default dynamic definition center
- treat `connectId` in entity metadata as the core entity-to-datasource binding
- treat `dynamicDao` as the preferred ORM execution entry for routed access
- enable JTA or Seata only when there is a clear need

## When To Read the Extension Chapter

If you only want to:

- understand how the current dynamic datasource works
- use the routing capability as provided
- switch datasource automatically by entity

this page is enough.

If you want to do things such as:

- replacing `DynamicDataSourceDefinitionLoader`
- loading datasource definitions from a config center or files
- overriding the default ORM `Dao` binding
- customizing a multi-`Dao` strategy
- enabling JTA or Seata and integrating further

then continue with:

- [ORM / Datasource Extension](../orm/datasource-extension.md)

## Suggested Reading

- [Binding & Priority](entity-binding.md)
- [Configuration](configuration.md)
- [Switching](switching.md)
- [ORM / Datasource Extension](../orm/datasource-extension.md)
- [ORM Overview](../orm/overview.md)
- [Fluent DSL Guide](../orm/fluent-dsl.md)
