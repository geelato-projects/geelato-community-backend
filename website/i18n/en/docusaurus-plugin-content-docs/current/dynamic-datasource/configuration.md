---
title: Dynamic Datasource Configuration
sidebar_label: Configuration
---

# Dynamic Datasource Configuration

This page covers every configuration entry of the dynamic datasource: where connection definitions live, what YAML can tune, how the default datasource is determined, and how to refresh after a connection change.

Configuration spans three layers:

- **Connection definitions**: the information of each physical database connection, provided by the definition-source SPI (the platform provides the default implementation)
- **Configuration properties**: pool sizing, lazy loading, catalog mapping, etc., under the prefix `geelato.datasource.dynamic.*`
- **ORM properties**: default datasource and Dao binding, under the prefix `geelato.orm.*`

## Connection Definitions: the SPI Contract

Connection definitions are provided by the `DynamicDataSourceDefinitionLoader` SPI; each definition is a Map whose `id` is the **routing key** — the connectId used by entity binding and switching APIs. The full field contract (`db_type` / `db_hostname_ip` / `db_port` / `db_user_name` / `db_name` / `db_password`, snake_case) and the password encryption convention are covered in [ORM / Datasource Extension](../orm/datasource-extension.md).

A few key points:

- `dbType` is built-in for `mysql` / `postgresql`; other types can plug in via a `Dialects` dialect-family alias registration (the platform materialized-view engine `risingwave` works this way, see [Materialized View Acceleration (Platform)](../platform-capabilities/materialized-view.md))
- pool construction is centralized in `DataSourceFactory`: it normalizes `dbType` into a dialect family, uses HikariCP, decrypts the password and applies unified pool properties
- for mapping intranet host addresses to local ones in development, see [Host Mapping](host-mapping.md)
- to switch the definition source to a config center, YAML or an external service, see [ORM / Datasource Extension](../orm/datasource-extension.md)

## Configuration Properties: `geelato.datasource.dynamic.*`

### Catalog Mapping

The mapping from `catalog` (logical database group) to datasource key, used together with `@Entity(catalog)`:

```yaml
geelato:
  datasource:
    dynamic:
      catalog-mapping:
        platform: primary
        business: biz_db
```

Entities declaring `@Entity(catalog = "business")` route to `biz_db`. The mapping is resolved at query time, so adding it after startup works too. Full priority rules in [Entity Binding](entity-binding.md).

### Pool and Timeouts

These properties apply to every dynamically built datasource (HikariCP):

| Property | Default | Description |
|---|---|---|
| `minimum-idle` | `5` | minimum idle connections |
| `maximum-pool-size` | `20` | maximum connections |
| `idle-timeout-ms` | `600000` | idle timeout (ms) |
| `max-lifetime-ms` | `1800000` | max connection lifetime |
| `connection-timeout-ms` | `5000` | timeout for borrowing from the pool |
| `validation-timeout-ms` | `3000` | connection validation timeout |
| `keepalive-time-ms` | `300000` | keepalive probe interval |
| `initialization-fail-timeout-ms` | `0` | init fail timeout (0 does not block startup) |
| `connection-test-query` | `SELECT 1` | validity test statement |
| `connect-timeout-ms` | `5000` | TCP connect timeout, appended to the JDBC URL |
| `socket-timeout-ms` | `300000` | socket read timeout, appended to the JDBC URL; longer queries are cut off by the driver |

### Lazy Loading and Transaction Enhancements

| Property | Default | Description |
|---|---|---|
| `delay-load-data-source` | `true` | lazy loading: cache definitions only at startup, build the pool when a key is first routed to. Friendly when many datasources are configured but few are active |
| `enable-jta-transaction` | `false` | wires the Atomikos JTA distributed-transaction extension point (optional; default stays lightweight local transactions) |
| `enable-seata-proxy` | `false` | wraps dynamic datasources with a Seata `DataSourceProxy` (requires your own Seata environment) |

JTA / Seata are reserved capabilities; ordinary business applications do not need them. See the overview for the current JTA / Seata status.

## ORM Properties: `geelato.orm.*`

| Property | Default | Description |
|---|---|---|
| `default-data-source-key` | auto-inferred | the platform default datasource key (level 4 of the priority chain). When unset: `primary` if any of `primaryDataSource` / `primaryJdbcTemplate` / `primaryDao` beans exists |
| `dao-bean-name` | auto-resolved | the Dao bean bound to the ORM. Resolution order: `dynamicDao` > `primaryDao` > the single Dao bean; usually no configuration needed |

`default-data-source-key` is resolved in the late startup phase (after all singleton beans are ready) by `OrmAutoConfiguration` and written into `DataSourceManager`. How the runtime priority chain consumes it is covered in [Entity Binding](entity-binding.md).

## Bean Wiring Conditions

Knowing which beans appear under which conditions helps diagnose "why didn't it switch":

- **Starter side** (when `spring.datasource.primary.jdbc-url` is present): `primaryDataSource` → `primaryJdbcTemplate` → `primaryDao`; with `spring.datasource.secondary.*` configured, `secondaryDataSource` and friends appear too. `primaryDao` connects straight to the physical primary and **bypasses routing**
- **Dynamic datasource stack** (auto-configured when `primaryJdbcTemplate` exists in the container): `dynamicDataSource` (routing) → `dynamicJdbcTemplate` → `dynamicDao`, plus the definition loader, registry and aspects
- applications not using the Starter can trigger the dynamic datasource stack by providing a bean named `primaryJdbcTemplate` themselves

The bean topology and "only `dynamicDao` can switch" are covered fully in [Switching](switching.md).

## Refreshing After a Connection Change

No restart is needed after connection definitions change. The framework provides two refresh entries, and **both steps are required** (full code in the extension page, "Runtime Hot Refresh"):

- `DynamicDataSourceRegistry.refreshDataSource(key)` / `refreshAllDataSources()` — update the definitions and instances in the registry (rebuilding the pool when necessary); deleted or disabled connections are removed from registration
- `DynamicRoutingDataSource.refreshDataSource(key)` / `refreshAllDataSources()` — reassemble the routing table

Under lazy loading, connections that never built a pool only get their definition updated and still build lazily on first route.

:::note Entity mapping cache and connection config snapshot

`EntityDataSourceResolver` keeps an in-memory cache of "entity → datasource" resolutions, but every hit re-checks that the target datasource is still registered; the cache invalidates itself when a datasource is removed. No extra cleanup of entity mappings is needed after refreshing connection definitions. On the core side, `DataSourceManager` also holds a snapshot of connection definitions (used by readers such as dbType resolution); on a snapshot miss it loads the row on demand via the definition loader's `loadOne` — connections created at runtime (e.g. a materialized-view engine connection) become visible to dbType resolution immediately, with no extra refresh.

:::

## Suggested Reading

- [Binding & Priority](entity-binding.md) — the four binding styles and the full priority chain
- [Switching](switching.md) — automatic switching, manual APIs and transaction limits
- [Host Mapping](host-mapping.md) — mapping intranet addresses locally for development
- [ORM / Datasource Extension](../orm/datasource-extension.md) — the definition-source SPI, the field contract and hot refresh
