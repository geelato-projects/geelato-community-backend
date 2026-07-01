# RequireTable

This page explains which foundation tables are required by `geelato-app-scaffold-starter` and how they are automatically checked and initialized during application startup.

Script directory:

- `geelato-app-scaffold-starter/init`

Startup initializer:

- `cn.geelato.app.scaffold.boot.AppScaffoldSchemaInitializer`

## Why These Tables Exist

`geelato-app-scaffold-starter` is not only a bare Web bootstrap starter.

It already carries platform runtime foundations such as:

- metadata tables
- dictionary
- organization
- user
- role and permission
- system configuration

So if a business project wants to consume scaffold capabilities directly, these base tables must exist.

## Current RequireTable List

The current starter contains these SQL scripts:

- `platform_dev_column.sql`
- `platform_dev_db_connect.sql`
- `platform_dev_table.sql`
- `platform_dev_table_check.sql`
- `platform_dev_table_foreign.sql`
- `platform_dev_view.sql`
- `platform_dict.sql`
- `platform_dict_item.sql`
- `platform_org.sql`
- `platform_org_r_user.sql`
- `platform_permission.sql`
- `platform_role.sql`
- `platform_role_r_permission.sql`
- `platform_role_r_user.sql`
- `platform_sys_config.sql`
- `platform_user.sql`
- `platform_user_r_permission.sql`

They can be grouped as:

- metadata and design-time base tables
- dictionary tables
- organization and user tables
- role and permission tables
- system configuration tables

## The SQL Is Already Inside the Starter

These scripts are already packaged with `geelato-app-scaffold-starter`.

Business projects are not expected to manually rewrite them.

## How Startup Auto-Initialization Works

`AppScaffoldSchemaInitializer` implements:

- `CommandLineRunner`
- `Ordered`

and runs with:

- `Ordered.HIGHEST_PRECEDENCE`

The current flow is:

1. scan `classpath*:geelato/app/scaffold/init/*.sql`
2. sort resources by file name
3. obtain a JDBC connection from `dao.getJdbcTemplate().getDataSource()`
4. resolve the target table name from each file name
5. check existence through `DatabaseMetaData.getTables(...)`
6. skip the script if the table already exists
7. execute the script in UTF-8 if the table does not exist

So the initializer is not a blind full replay. It is a startup existence check plus first-time creation mechanism.

## Important Boundary

This capability is suitable for:

- first startup of a fresh database
- scaffold foundation table initialization
- first integration of a business project with the starter

It does not handle:

- schema evolution of existing tables
- column additions
- column type changes
- automatic `ALTER TABLE`
- complex versioned migrations

So it should be understood as a first-time schema initializer, not as a full database migration framework.

## What This Means for Business Projects

The recommended responsibility boundary is:

- the starter owns scaffold foundation tables
- the business project owns its own business tables
- business SQL scripts go under `src/main/resources/geelato/app/scaffold/init/`

## Suggested Reading

- [Project Guide](app-scaffold.md)
- [System Configuration](../system-config/overview.md)
- [Dynamic Datasource](../dynamic-datasource/overview.md)
