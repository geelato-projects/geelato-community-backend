---
title: Dynamic Datasource Configuration
sidebar_label: Configuration
---

# Dynamic Datasource Configuration

This page covers every configuration entry of the dynamic datasource: where connection definitions live, what YAML can tune, how the default datasource is determined, and how to refresh after a connection change.

Configuration spans three layers:

- **Connection definitions**: the information of each physical database connection, stored in the platform table `platform_dev_db_connect`
- **Module properties**: pool sizing, lazy loading, catalog mapping, etc., under the prefix `geelato.datasource.dynamic.*`
- **ORM properties**: default datasource and Dao binding, under the prefix `geelato.orm.*`

## Connection Definitions: the `platform_dev_db_connect` Table

The default source of truth for dynamic datasource definitions is the connection table in the platform primary database, mapped by the entity `ConnectMeta`:

| Field | Column | Description |
|---|---|---|
| `id` | `id` | primary key, i.e. the **routing key** — the connectId used by entity binding and switching APIs is this id |
| `appId` | `app_id` | owning application |
| `dbConnectName` | `db_connect_name` | connection name (required) |
| `dbName` | `db_name` | database name (required) |
| `dbSchema` | `db_schema` | database schema (required) |
| `dbType` | `db_type` | database type (required), currently `mysql` / `postgresql` |
| `dbUserName` | `db_user_name` | username (required) |
| `dbPassword` | `db_password` | password (required, **stored encrypted**, decrypted automatically when the pool is built) |
| `dbPort` | `db_port` | connection port |
| `dbHostnameIp` | `db_hostname_ip` | hostname or IP (required) |
| `enableStatus` | `enable_status` | enable status, 1 enabled / 0 disabled |

A few key points:

- **routing key aligns with the primary key**. The `xxx` in `@Entity(connectId = "xxx")`, `useDataSource("xxx")` and `switchDbByConnectId("xxx")` is always the `id` of this table
- pool construction is centralized in `DataSourceFactory`: it picks the dialect by `dbType` (MySQL / PostgreSQL only), uses HikariCP, decrypts the password and applies unified pool properties
- for mapping intranet host addresses to local ones in development, see [Host Mapping](host-mapping.md)
- to load definitions from a config center, YAML or an external service instead, see [ORM / Datasource Extension](../orm/datasource-extension.md)

## Module Properties: `geelato.datasource.dynamic.*`

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

Connection definitions live in the database; after changing them there is no restart needed — refresh through the platform endpoints:

- `POST /model/connect/refresh/{id}` — refresh a single connection (reload the definition, rebuild that key's datasource)
- `POST /model/connect/refreshAll` — refresh everything

Refreshing is done by `DevDbConnectService`: definitions and instances are updated in the registry first, then the routing map of the routing datasource is refreshed; deleted or disabled connections are removed from routing. Under lazy loading, connections that never built a pool only get their definition updated and still build lazily on first route.

:::note Entity mapping cache

`EntityDataSourceResolver` keeps an in-memory cache of "entity → datasource" resolutions, but every hit re-checks that the target datasource is still registered; the cache invalidates itself when a datasource is removed. No extra cleanup of entity mappings is needed after refreshing connection definitions.

:::

## Suggested Reading

- [Binding & Priority](entity-binding.md) — the four binding styles and the full priority chain
- [Switching](switching.md) — automatic switching, manual APIs and transaction limits
- [Host Mapping](host-mapping.md) — mapping intranet addresses locally for development
