# ORM / DataSource Extension

This page explains the current ORM and dynamic datasource extension surface and how a host application can override defaults without breaking the starter contract.

If you want to understand how the built-in dynamic datasource capability works before customizing it, read this first:

- [Dynamic Datasource](../dynamic-datasource/overview.md)

## Current ORM Entry

The current ORM auto-configuration entry is `OrmAutoConfiguration`, which mainly creates:

- `MetaCommandExecutor`
- `SaveDefaultValueFiller`

`MetaCommandExecutor` is created only when a `Dao` bean exists in the host application.

## Which DAO Is Used

The explicit ORM property is:

```properties
geelato.orm.dao-bean-name=dynamicDao
```

If the property is configured, ORM binds to the named `Dao` bean.

If it is not configured and multiple `Dao` beans exist, the current compatibility order is:

1. `dynamicDao`
2. `primaryDao`

## Starter JDBC Defaults

When `spring.datasource.primary.jdbc-url` exists, the starter creates:

- `primaryDataSource`
- `primaryJdbcTemplate`
- `primaryDao`
- `dbGenerateDao`

If `spring.datasource.secondary.jdbc-url` also exists, it additionally creates:

- `secondaryDataSource`
- `secondaryJdbcTemplate`
- `secondaryDao`

## Dynamic Datasource Extension Points

The dynamic datasource property prefix is:

```properties
geelato.datasource.dynamic.*
```

Important defaults include:

- `delay-load-data-source=true`
- `enable-jta-transaction=false`
- `enable-seata-proxy=false`
- default pool settings and `connection-test-query=SELECT 1`

## Default Definition Source

The current default definition loader is:

- `PlatformDynamicDataSourceDefinitionLoader`

It is created only when the host application does not provide its own `DynamicDataSourceDefinitionLoader`.

## How To Replace It

The host application only needs to provide its own:

- `DynamicDataSourceDefinitionLoader`

Typical scenarios include reading datasource definitions from:

- a configuration center
- YAML or files
- an external registry service

## When JTA / Seata Is Enabled

JTA and Seata are not enabled by default.

They are only activated after explicit configuration, for example:

```properties
geelato.datasource.dynamic.enable-jta-transaction=true
```

## Suggested Reading

- [Dynamic Datasource](../dynamic-datasource/overview.md)
- [ORM Overview](overview.md)
- [Override Default Implementations](../reference/override-default-implementations.md)
- [Minimal Integration](../guide/minimal-integration.md)
