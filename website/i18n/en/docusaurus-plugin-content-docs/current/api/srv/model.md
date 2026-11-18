---
title: API · Data Modeling
sidebar_label: Data Modeling
---
# Data Modeling (model)

7 controllers, 48 endpoints in total. All endpoints in this domain are design-time APIs (@DesignTimeApiRestController), intended primarily for the visual designers.

## DevDbConnectController ⚙ — `/model/connect`

Design-time management of database connections (ConnectMeta): CRUD, connection testing and dynamic data source refresh.

### pageQuery Page query connections

| Item | Content |
| --- | --- |
| URL | `POST /api/model/connect/pageQuery` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiPagedResult<ConnectMeta>`: paged list of database connection configurations (connect name, db type, host, port, db name, schema, user name, etc.). |

### query Query all connections

| Item | Content |
| --- | --- |
| URL | `GET /api/model/connect/query` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiResult<List<ConnectMeta>>`: list of connection configurations (method signature declares a raw `ApiResult`). |

### get Connection detail

| Item | Content |
| --- | --- |
| URL | `GET /api/model/connect/get/{id}` |
| Params | `id`: path, string, required, connection ID. |
| Returns | `ApiResult<ConnectMeta>`: connection configuration detail (method signature declares a raw `ApiResult`). |

### createOrUpdate Create or update connection

| Item | Content |
| --- | --- |
| URL | `POST /api/model/connect/createOrUpdate` |
| Params | `form`: body, ConnectMeta object, required. Common fields: connect name `dbConnectName`, db type `dbType`, host `dbHostnameIp`, port `dbPort`, database `dbName`, schema `dbSchema`, user name `dbUserName`, password `dbPassword`. |
| Returns | `ApiResult<ConnectMeta>`: first checks duplicates by type+host+port+db name+app+tenant (fails on duplicate); with a non-blank `id` it updates (schema changes upgrade metadata accordingly), otherwise it creates and immediately refreshes the dynamic data source. Returns the saved connection. |

### isDelete Delete connection

| Item | Content |
| --- | --- |
| URL | `DELETE /api/model/connect/isDelete/{id}` |
| Params | `id`: path, string, required, connection ID. |
| Returns | `ApiResult<NullResult>`: fails when the connection is referenced by tables/views/checks; otherwise marks it disabled and soft-deletes. `data` is empty. |

### jdbcConnect Test JDBC connection

| Item | Content |
| --- | --- |
| URL | `POST /api/model/connect/jdbcConnect` |
| Params | `form`: body, ConnectMeta object, required; the driver and URL format are resolved from `dbType`. |
| Returns | `ApiResult<Boolean>`: whether a valid JDBC connection can be established. |

### batchCreate Batch copy connections

| Item | Content |
| --- | --- |
| URL | `POST /api/model/connect/batchCreate` |
| Params | `appId`: body, string, required, target app ID.<br />`connectIds`: body, string array, required, source connection IDs.<br />`userName`: body, string, required, database user name.<br />`password`: body, string, required, database password. |
| Returns | `ApiResult<NullResult>`: copies the source connections to new ones (overriding user name, password and app); fails when an identical connection already exists. `data` is empty. |

### refresh Refresh one data source

| Item | Content |
| --- | --- |
| URL | `POST /api/model/connect/refresh/{id}` |
| Params | `id`: path, string, required, connection ID. |
| Returns | `ApiResult<Boolean>`: whether the refresh succeeded; fails when the connection does not exist or the dynamic data source capability is disabled. |

### refreshAll Refresh all data sources

| Item | Content |
| --- | --- |
| URL | `POST /api/model/connect/refreshAll` |
| Params | None |
| Returns | `ApiResult<Integer>`: number of refreshed data sources; fails when the dynamic data source capability is disabled. |

## DevTableCheckController ⚙ — `/model/table/check`

Design-time CRUD and uniqueness validation of table check constraints (TableCheck).

### pageQuery Page query checks

| Item | Content |
| --- | --- |
| URL | `POST /api/model/table/check/pageQuery` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiPagedResult<DataItems>`: `data` is a `DataItems` (`total` plus `items`, the list of check constraint records). |

### query Query all checks

| Item | Content |
| --- | --- |
| URL | `GET /api/model/table/check/query` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiResult<List<TableCheck>>`: list of check constraints (method signature declares a raw `ApiResult`). |

### get Check detail

| Item | Content |
| --- | --- |
| URL | `GET /api/model/table/check/get/{id}` |
| Params | `id`: path, string, required, check constraint ID. |
| Returns | `ApiResult<TableCheck>`: check constraint detail (method signature declares a raw `ApiResult`). |

### createOrUpdate Create or update check

| Item | Content |
| --- | --- |
| URL | `POST /api/model/table/check/createOrUpdate` |
| Params | `form`: body, TableCheck object, required. Common fields: title `title`, code `code`, type `type`, check clause `checkClause`, owning table `tableId`, related column `columnId` (`connectId`, `tableName`, etc. are filled from the owning table). |
| Returns | `ApiResult<TableCheck>`: saved with `synced` set to not-synced, and the entity metadata cache of the owning table is refreshed (method signature declares a raw `ApiResult`). |

### isDelete Delete check

| Item | Content |
| --- | --- |
| URL | `DELETE /api/model/table/check/isDelete/{id}` |
| Params | `id`: path, string, required, check constraint ID. |
| Returns | `ApiResult<NullResult>`: soft-deletes and refreshes the entity metadata cache of the owning table. `data` is empty. |

### validate Validate check name

| Item | Content |
| --- | --- |
| URL | `POST /api/model/table/check/validate` |
| Params | `form`: body, TableCheck object, required; `tableId` and `type` must not be blank, `code` is the uniqueness key. |
| Returns | `ApiResult<Boolean>`: first checks `platform_dev_table_check` for an existing record with the same code and type, then switches to the target connection to check the database for a check constraint with the same name; returns `true` only when neither exists (`false` when the connection is missing or something exists). |

## DevTableColumnController ⚙ — `/model/table/column`

Design-time CRUD of table columns (ColumnMeta), plus default column templates, select types and name-uniqueness validation.

### pageQuery Page query columns

| Item | Content |
| --- | --- |
| URL | `POST /api/model/table/column/pageQuery` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiPagedResult<ColumnMeta>`: paged list of columns. |

### query Query all columns

| Item | Content |
| --- | --- |
| URL | `GET /api/model/table/column/query` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiResult<List<ColumnMeta>>`: list of columns (method signature declares a raw `ApiResult`). |

### get Column detail

| Item | Content |
| --- | --- |
| URL | `GET /api/model/table/column/get/{id}` |
| Params | `id`: path, string, required, column ID. |
| Returns | `ApiResult<ColumnMeta>`: column detail (method signature declares a raw `ApiResult`). |

### createOrUpdate Create or update column

| Item | Content |
| --- | --- |
| URL | `POST /api/model/table/column/createOrUpdate` |
| Params | `form`: body, ColumnMeta object, required. Common fields: column name `name`, title `title`, column type `type`/`dataType`, owning table `tableId`, select type `selectType`, auto add `autoAdd`/`autoName`, etc. |
| Returns | `ApiResult<ColumnMeta>`: on update, a renamed column is first copied in the database (keeping the old one) and column permissions are adjusted; on create, it is marked not-synced and table default column permissions are reset; when the select type is organization/user with auto-add enabled, related columns are generated automatically. Finally the default view and entity metadata cache are refreshed (method signature declares a raw `ApiResult`). |

### insertCommon Copy columns into table

| Item | Content |
| --- | --- |
| URL | `POST /api/model/table/column/insertCommon` |
| Params | `tableId`: body, string, required, target table ID.<br />`columnIds`: body, string, required, IDs of the columns to copy, comma-separated.<br />`tableName`: body, string, read by the code but not used. |
| Returns | `ApiResult<NullResult>`: copies existing columns into the target table (skipping same names, marked not-synced) and refreshes the default view and entity metadata cache. `data` is empty. |

### isDelete Delete column

| Item | Content |
| --- | --- |
| URL | `DELETE /api/model/table/column/isDelete/{id}` |
| Params | `id`: path, string, required, column ID. |
| Returns | `ApiResult<NullResult>`: soft-deletes and refreshes the default view and entity metadata cache. `data` is empty. |

### queryDefaultMeta Default column templates

| Item | Content |
| --- | --- |
| URL | `GET /api/model/table/column/queryDefaultMeta` |
| Params | None |
| Returns | `ApiResult<List<ColumnMeta>>`: list of built-in default column templates. |

### selectType Column select types

| Item | Content |
| --- | --- |
| URL | `GET /api/model/table/column/selectType` |
| Params | None |
| Returns | `ApiResult<List<ColumnSelectType>>`: selectable data types for columns (group `group`, label `label`, value `value`, database type `mysql`, disabled flag, default length, value range, etc.). |

### validate Validate column name

| Item | Content |
| --- | --- |
| URL | `POST /api/model/table/column/validate` |
| Params | `form`: body, ColumnMeta object, required; uses `name`, `tableId`, `appId`, `tenantCode`. |
| Returns | `ApiResult<Boolean>`: checks whether the column name is duplicated within the same table (queries `platform_dev_column`); `true` when available. |

## DevTableController ⚙ — `/model/table`

Design-time CRUD of tables (TableMeta) with copy, upgrade, default view and database-driven reset.

### pageQuery Page query tables

| Item | Content |
| --- | --- |
| URL | `POST /api/model/table/pageQuery` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiPagedResult<TableMeta>`: paged list of tables. |

### query Query all tables

| Item | Content |
| --- | --- |
| URL | `GET /api/model/table/query` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiResult<List<TableMeta>>`: list of tables. |

### get Table detail

| Item | Content |
| --- | --- |
| URL | `GET /api/model/table/get/{id}` |
| Params | `id`: path, string, required, table ID. |
| Returns | `ApiResult<TableMeta>`: table detail. |

### createOrUpdate Create or update table

| Item | Content |
| --- | --- |
| URL | `POST /api/model/table/createOrUpdate` |
| Params | `form`: body, TableMeta object, required. Common fields: entity name `entityName`, title `title`, connection `connectId`, table type `tableType`, source `sourceType`, etc. (`tableSchema` and `dbType` are filled from the connection). |
| Returns | `ApiResult<TableMeta>`: on create, marks not-synced, initializes table default permissions and creates default columns for non-third-party sources; on update, an entity-name change backs up the old table and renames it in the database (adjusting permissions and refreshing the default view). Finally the entity metadata cache is refreshed; returns the saved table (signature declares `ApiResult<?>`). |

### copy Copy table

| Item | Content |
| --- | --- |
| URL | `POST /api/model/table/copy` |
| Params | `id`: body, string, required, source table ID.<br />`entityName`: body, string, required, entity name of the new table.<br />`title`: body, string, optional, title of the new table.<br />`connectId`: body, string, optional, connection of the new table.<br />`tableComment`: body, string, optional, comment of the new table.<br />`appId`: body, string, optional, app ID.<br />`tenantCode`: body, string, optional, tenant code. |
| Returns | `ApiResult<TableMeta>`: copies the source table and its columns into a new table (source type reset to manual creation), initializes default permissions and refreshes the entity cache. |

### upgradesTable Upgrade table structure

| Item | Content |
| --- | --- |
| URL | `POST /api/model/table/upgradesTable` |
| Params | `tableId`: body, string, required, target table ID.<br />`columnNames`: body, string, required, comma-separated upgrade-template column names.<br />`upgradeType`: body, string, optional, contains `app` to enable cross-app and `workflow` to enable cross-workflow. |
| Returns | `ApiResult<NullResult>`: adds the upgrade-template columns missing from the table, updates the cross-app/cross-workflow flags per `upgradeType` and refreshes the entity cache; `data` is empty on success (signature declares `ApiResult<?>`). |

### isDelete Delete table

| Item | Content |
| --- | --- |
| URL | `DELETE /api/model/table/isDelete/{id}` |
| Params | `id`: path, string, required, table ID. |
| Returns | `ApiResult<NullResult>`: soft-deletes (the database table is renamed with a `_d` timestamp suffix and the title/comment get a "deleted" prefix) and removes the entity metadata cache; `data` is empty. |

### queryDefaultView Default view SQL

| Item | Content |
| --- | --- |
| URL | `GET /api/model/table/queryDefaultView/{entityName}` |
| Params | `entityName`: path, string, required, entity name. |
| Returns | `ApiResult<String>`: the default view SQL (`viewConstruct`) generated from the entity columns. |

### resetDefaultView Reset default view

| Item | Content |
| --- | --- |
| URL | `POST /api/model/table/resetDefaultView` |
| Params | `form`: body, TableMeta object, required; `entityName` must not be blank (matched together with `id` and `connectId`). |
| Returns | `ApiResult<NullResult>`: rebuilds or updates the default view for the matched tables. `data` is empty. |

### resetModelFormTable Reset table from database

| Item | Content |
| --- | --- |
| URL | `POST /api/model/table/reset/{tableId}` |
| Params | `tableId`: path, string, required, table ID; disabled tables cannot be reset. |
| Returns | `ApiMetaResult<NullResult>`: syncs the table definition from the actual database structure (updates the table info and resets columns and foreign keys; when the table no longer exists in the database it is soft-deleted). `data` is empty (with the extra `meta`). |

### validate Validate entity name

| Item | Content |
| --- | --- |
| URL | `POST /api/model/table/validate` |
| Params | `form`: body, TableMeta object, required; uses `entityName`, `connectId`, `appId`, `tenantCode`. |
| Returns | `ApiResult<Boolean>`: checks whether the entity name is duplicated within the same connection; when conflict detection is enabled and the name clashes with a loaded Java built-in entity, a failure message is returned directly. |

## DevTableForeignController ⚙ — `/model/table/foreign`

Design-time CRUD of table foreign keys / table relations (TableForeign).

### pageQuery Page query foreign keys

| Item | Content |
| --- | --- |
| URL | `POST /api/model/table/foreign/pageQuery` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiPagedResult<TableForeign>`: paged list of foreign keys. |

### query Query all foreign keys

| Item | Content |
| --- | --- |
| URL | `GET /api/model/table/foreign/query` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiResult<List<TableForeign>>`: list of foreign keys (method signature declares a raw `ApiResult`). |

### get Foreign key detail

| Item | Content |
| --- | --- |
| URL | `GET /api/model/table/foreign/get/{id}` |
| Params | `id`: path, string, required, foreign key ID. |
| Returns | `ApiResult<TableForeign>`: foreign key detail. |

### createOrUpdate Create or update foreign key

| Item | Content |
| --- | --- |
| URL | `POST /api/model/table/foreign/createOrUpdate` |
| Params | `form`: body, TableForeign object, required. Common fields: main table `mainTable`/`mainTableId`/`mainTableCol`, foreign table `foreignTable`/`foreignTableId`/`foreignTableCol`, delete action `deleteAction`, update action `updateAction`, etc. |
| Returns | `ApiResult<TableForeign>`: updates when `id` is non-blank, otherwise creates (method signature declares a raw `ApiResult`). |

### isDelete Delete foreign key

| Item | Content |
| --- | --- |
| URL | `DELETE /api/model/table/foreign/isDelete/{id}` |
| Params | `id`: path, string, required, foreign key ID. |
| Returns | `ApiResult<NullResult>`: marks disabled and soft-deletes. `data` is empty. |

## DevTableImportController ⚙ — `/model/table/import`

Raw-JSON import of tables/columns/checks, either preserving external primary keys or letting the system generate new ones.

### importRaw Import preserving IDs

| Item | Content |
| --- | --- |
| URL | `POST /api/model/table/import/preserveId` |
| Params | `bodyJson`: body, string (raw JSON text), required, the import JSON must contain a `table` (object) and a `columns` (array) node, with `checks` (array) optional; every record in all three parts must carry an `id`.<br />`ddl`: query, boolean, optional, default `true`, whether to create/update the database table structure right after a successful import. |
| Returns | `ApiResult<Map<String,Object>>`: import counts `tables`/`columns`/`checks`; all records are saved as-is under their external IDs (`forceId` preserves the primary key), audit fields are overwritten with the current user/tenant/time, and the entity metadata cache is refreshed afterwards. |

### importCreate Import with new IDs

| Item | Content |
| --- | --- |
| URL | `POST /api/model/table/import/create` |
| Params | `bodyJson`: body, string (raw JSON text), required, the import JSON must contain a `table` (object) and a `columns` (array) node, with `checks` (array) optional; every record in all three parts must NOT carry an `id`.<br />`ddl`: query, boolean, optional, default `true`, whether to create/update the database table structure right after a successful import. |
| Returns | `ApiResult<Map<String,Object>>`: import counts `tables`/`columns`/`checks`; all records are saved with new system-generated IDs, audit fields are overwritten with the current user/tenant/time, and the entity metadata cache is refreshed afterwards. |

## DevViewController ⚙ — `/model/view`

Design-time CRUD and name-uniqueness validation of views (TableView).

### pageQuery Page query views

| Item | Content |
| --- | --- |
| URL | `POST /api/model/view/pageQuery` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiPagedResult<TableView>`: paged list of views. |

### query Query all views

| Item | Content |
| --- | --- |
| URL | `GET /api/model/view/query` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiResult<List<TableView>>`: list of views (method signature declares a raw `ApiResult`). |

### get View detail

| Item | Content |
| --- | --- |
| URL | `GET /api/model/view/get/{id}` |
| Params | `id`: path, string, required, view ID. |
| Returns | `ApiResult<TableView>`: view detail; the view column configuration `viewColumn` is converted into column metadata structures. |

### createOrUpdate Create or update view

| Item | Content |
| --- | --- |
| URL | `POST /api/model/view/createOrUpdate` |
| Params | `form`: body, TableView object, required. Common fields: view name `viewName`, view type `viewType`, construct SQL `viewConstruct`, view columns `viewColumn`, connection `connectId`, etc. |
| Returns | `ApiResult<TableView>`: before saving, the view columns are mapped into database object structures; after saving, the metadata cache is refreshed by view name (method signature declares a raw `ApiResult`). |

### isDelete Delete view

| Item | Content |
| --- | --- |
| URL | `DELETE /api/model/view/isDelete/{id}` |
| Params | `id`: path, string, required, view ID. |
| Returns | `ApiResult<NullResult>`: marks disabled and soft-deletes (the database view is renamed for archival, except materialized views) and removes the view metadata cache; `data` is empty. |

### validate Validate view name

| Item | Content |
| --- | --- |
| URL | `POST /api/model/view/validate` |
| Params | `form`: body, TableView object, required; uses `viewName`, `connectId`, `appId`, `tenantCode`. |
| Returns | `ApiResult<Boolean>`: checks whether the view name is duplicated within the same connection (queries `platform_dev_view`); `true` when available. |
