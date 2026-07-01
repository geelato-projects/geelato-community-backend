# Override Default Implementations

One of the framework rules is to keep useful module-internal defaults without turning them into mandatory framework contracts.

## What Can Be Overridden

The current replaceable areas include:

- `MetaStore`
- `MetaResourceProvider`
- `MetaBootstrap`
- `DynamicDataSourceDefinitionLoader`
- `MetaCommandExecutor`
- `SaveDefaultValueFiller`
- `OrgProvider`
- `UserProvider`
- starter-provided beans such as `primaryDataSource`, `primaryJdbcTemplate`, and `primaryDao`

## Option 1: Provide Your Own Bean

If the default bean is protected by `@ConditionalOnMissingBean`, the host application can simply register its own bean.

This applies to:

- `MetaCommandExecutor`
- `SaveDefaultValueFiller`
- `DynamicDataSourceDefinitionLoader`
- default security provider implementations

## Option 2: Override Named Infrastructure Beans

The starter currently creates these beans by name if they are missing:

- `primaryDataSource`
- `primaryJdbcTemplate`
- `primaryDao`
- `secondaryDataSource`
- `secondaryJdbcTemplate`
- `secondaryDao`
- `dbGenerateDao`

If the host application provides beans with those names first, the starter defaults are skipped.

## Option 3: Select the DAO by Property

ORM currently supports:

```properties
geelato.orm.dao-bean-name=dynamicDao
```

If the property is not configured and multiple `Dao` beans exist, the current compatibility order is:

1. `dynamicDao`
2. `primaryDao`

## Suggested Reading

- [MetaStore Extension](metastore-extension.md)
- [ORM / DataSource Extension](../orm/datasource-extension.md)
- [Security Provider Extension](security-provider-extension.md)
- [Default Implementations vs Sample](../guide/default-implementation-vs-sample.md)
