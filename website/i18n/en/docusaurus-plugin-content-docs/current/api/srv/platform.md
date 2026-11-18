---
title: API · Apps & Platform Config
sidebar_label: Apps & Platform Config
---
# Apps & Platform Config (platform)

13 controllers, 80 endpoints in total. Endpoints marked with ⚙ are design-time APIs.

## AppController — `/app`

App registration and version package import, filtering visible apps by user identity, and app page permission queries.

### pageQuery Paged query

| Item | Content |
| --- | --- |
| URL | `POST /api/app/pageQuery` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiPagedResult`: `data` is a page of apps (`App`). |

### query List query

| Item | Content |
| --- | --- |
| URL | `GET /api/app/query` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiResult<List<App>>`: list of apps. |

### queryByUser Query apps by user

| Item | Content |
| --- | --- |
| URL | `GET /api/app/queryByUser` |
| Params | `tenantCode`: query, string, required, tenant code.<br />`userId`: query, string, optional, user ID; defaults to the current logged-in user. |
| Returns | `ApiResult<List<Map<String,Object>>>`: `data` is the list of apps visible to the user, filtered by user type: employees/virtual employees only see normal apps (inside or in-out), tenant admins and system accounts see normal and platform apps, external users only see normal apps marked outside. |

### get Get details

| Item | Content |
| --- | --- |
| URL | `GET /api/app/get/{id}` |
| Params | `id`: path, string, app ID. |
| Returns | `ApiResult<App>`: app details. |

### createOrUpdate Create or update

| Item | Content |
| --- | --- |
| URL | `POST /api/app/createOrUpdate` |
| Params | `form`: body, `App` entity; non-empty `id` updates, empty `id` creates.<br />`useType`: query, string, optional; when set to `import` the import flow runs (requires non-empty `form.id`, imports the app by its existing ID). |
| Returns | `ApiResult<App>`: the saved or imported app. |

### isDelete Delete app

| Item | Content |
| --- | --- |
| URL | `DELETE /api/app/isDelete/{id}` |
| Params | `id`: path, string, app ID. |
| Returns | `ApiResult<NullResult>`: `data` is empty; fails if the app does not exist (soft delete). |

### validate Validate uniqueness

| Item | Content |
| --- | --- |
| URL | `POST /api/app/validate/{type}` |
| Params | `type`: path, string, check type: `code` checks app code availability, `id` checks whether the app ID already exists.<br />`form`: body, `App` entity, provides the `code`, `id`, `tenantCode` to validate. |
| Returns | `ApiResult<Boolean>`: `true` means available (code not taken or ID not present); fails when `type` is invalid. |

### queryPermissionByPage App page permission query

| Item | Content |
| --- | --- |
| URL | `GET /api/app/queryPermissionByPage` |
| Params | `appId`: query, string, optional, filter by app.<br />`tenantCode`: query, string, optional, filter by tenant.<br />`type`: query, string, optional, permission type.<br />`treeId`: query, string, optional, app page tree node ID. |
| Returns | `ApiResult<List<Map<String,Object>>>`: `data` is a list of page permission records (permission ID, name, code, type, object, rule, and owning page tree node ID). |

### queryRolePermissionByPage Role page permission query

| Item | Content |
| --- | --- |
| URL | `GET /api/app/queryRolePermissionByPage` |
| Params | `appId`: query, string, optional, filter by app.<br />`roleId`: query, string, optional, role ID; multiple IDs comma-separated.<br />`type`: query, string, optional, permission type.<br />`tenantCode`: query, string, optional, filter by tenant. |
| Returns | `ApiResult<List<Map<String,Object>>>`: `data` is a list of role-to-page-permission records (role, permission, and owning app). |

### upload Upload app package

| Item | Content |
| --- | --- |
| URL | `POST /api/app/upload/{fileId}` |
| Params | `fileId`: path, string, attachment ID of the uploaded app package (`.gdp` archive). |
| Returns | `ApiResult<App>`: `data` is the imported/updated app; the archive must contain a `.gdp` descriptor (source app ID, name, code, version); fails when the file is missing or cannot be parsed. |

## AppMultiLangController — `/app/multiLang`

App multi-language resource maintenance and language package content reading.

### pageQuery Paged query

| Item | Content |
| --- | --- |
| URL | `POST /api/app/multiLang/pageQuery` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiPagedResult`: `data` is a page of app multi-language records (`AppMultiLang`). |

### query List query

| Item | Content |
| --- | --- |
| URL | `GET /api/app/multiLang/query` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiResult<List<AppMultiLang>>`: list of app multi-language records. |

### get Get details

| Item | Content |
| --- | --- |
| URL | `GET /api/app/multiLang/get/{id}` |
| Params | `id`: path, string, multi-language record ID. |
| Returns | `ApiResult<AppMultiLang>`: multi-language record details. |

### createOrUpdate Create or update

| Item | Content |
| --- | --- |
| URL | `POST /api/app/multiLang/createOrUpdate` |
| Params | `form`: body, `AppMultiLang` entity; non-empty `id` updates, empty `id` creates. |
| Returns | `ApiResult<AppMultiLang>`: the saved multi-language record. |

### isDelete Delete multi-language record

| Item | Content |
| --- | --- |
| URL | `DELETE /api/app/multiLang/isDelete/{id}` |
| Params | `id`: path, string, multi-language record ID. |
| Returns | `ApiResult<NullResult>`: `data` is empty. |

### queryAml Read language package

| Item | Content |
| --- | --- |
| URL | `GET /api/app/multiLang/queryAml/{appId}/{langType}` |
| Params | `appId`: path, string, app ID.<br />`langType`: path, string, language type. |
| Returns | `ApiResult<String>`: `data` is the language package file content for the app and language (purpose fixed to `WEBAPP`; takes the latest record and reads its package file; `data` is empty when nothing matches). |

## AppSqlMapController — `/app/sql`

App-to-SQL authorization mapping: manages the grants that let one app use another app's custom SQL.

### pageQuery Paged query

| Item | Content |
| --- | --- |
| URL | `POST /api/app/sql/pageQuery` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiPagedResult`: `data` is a page of app SQL grants (`AppSqlMap`). |

### pageQueryOf Paged query (joined view)

| Item | Content |
| --- | --- |
| URL | `GET /api/app/sql/pageQueryOf` |
| Params | `query`: query, key-value filters passed as-is to the named query (e.g. `appId`, `tenantCode`).<br />Pagination params: see [Conventions](../reference.md) |
| Returns | `ApiPagedResult`: `data` is a page of app-SQL grant records (Map structure) from the named query `page_query_platform_app_r_sql`. |

### query List query

| Item | Content |
| --- | --- |
| URL | `GET /api/app/sql/query` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiResult<List<AppSqlMap>>`: list of app SQL grants. |

### get Get details

| Item | Content |
| --- | --- |
| URL | `GET /api/app/sql/get/{id}` |
| Params | `id`: path, string, grant record ID. |
| Returns | `ApiResult<AppSqlMap>`: grant record details. |

### createOrUpdate Create or update

| Item | Content |
| --- | --- |
| URL | `POST /api/app/sql/createOrUpdate` |
| Params | `form`: body, `AppSqlMap` entity; non-empty `id` updates, empty `id` creates; field pre-processing (`after`) runs before saving. |
| Returns | `ApiResult<AppSqlMap>`: the saved grant record. |

### isDelete Delete grant

| Item | Content |
| --- | --- |
| URL | `DELETE /api/app/sql/isDelete/{id}` |
| Params | `id`: path, string, grant record ID. |
| Returns | `ApiResult<NullResult>`: `data` is empty. |

## AppTableMapController — `/app/table`

App-to-table mapping: manages the table grants accessible to an app.

### pageQuery Paged query

| Item | Content |
| --- | --- |
| URL | `GET /api/app/table/pageQuery` |
| Params | Pagination and filter params: see [Conventions](../reference.md); params are passed in a JSON request body (GET with body). |
| Returns | `ApiPagedResult`: `data` is a page of app table mappings (`AppTableMap`). |

### pageQueryOf Paged query (joined view)

| Item | Content |
| --- | --- |
| URL | `GET /api/app/table/pageQueryOf` |
| Params | `query`: query, key-value filters passed as-is to the named query (e.g. `appId`, `tenantCode`).<br />Pagination params: see [Conventions](../reference.md) |
| Returns | `ApiPagedResult`: `data` is a page of app-to-table mapping records (Map structure) from the named query `page_query_platform_app_r_table`. |

### query List query

| Item | Content |
| --- | --- |
| URL | `GET /api/app/table/query` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiResult<List<AppTableMap>>`: list of app table mappings. |

### get Get details

| Item | Content |
| --- | --- |
| URL | `GET /api/app/table/get/{id}` |
| Params | `id`: path, string, mapping record ID. |
| Returns | `ApiResult<AppTableMap>`: mapping record details. |

### createOrUpdate Create or update

| Item | Content |
| --- | --- |
| URL | `POST /api/app/table/createOrUpdate` |
| Params | `form`: body, `AppTableMap` entity; non-empty `id` updates, empty `id` creates; field pre-processing (`after`) runs before saving. |
| Returns | `ApiResult<AppTableMap>`: the saved mapping record. |

### isDelete Delete mapping

| Item | Content |
| --- | --- |
| URL | `DELETE /api/app/table/isDelete/{id}` |
| Params | `id`: path, string, mapping record ID. |
| Returns | `ApiResult<NullResult>`: `data` is empty. |

## AppViewMapController — `/app/view`

App-to-view mapping: manages the view grants accessible to an app.

### pageQuery Paged query

| Item | Content |
| --- | --- |
| URL | `POST /api/app/view/pageQuery` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiPagedResult`: `data` is a page of app view mappings (`AppViewMap`). |

### pageQueryOf Paged query (joined view)

| Item | Content |
| --- | --- |
| URL | `GET /api/app/view/pageQueryOf` |
| Params | `query`: query, key-value filters passed as-is to the named query (e.g. `appId`, `tenantCode`).<br />Pagination params: see [Conventions](../reference.md) |
| Returns | `ApiPagedResult`: `data` is a page of app-to-view mapping records (Map structure) from the named query `page_query_platform_app_r_view`. |

### query List query

| Item | Content |
| --- | --- |
| URL | `GET /api/app/view/query` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiResult<List<AppViewMap>>`: list of app view mappings. |

### get Get details

| Item | Content |
| --- | --- |
| URL | `GET /api/app/view/get/{id}` |
| Params | `id`: path, string, mapping record ID. |
| Returns | `ApiResult<AppViewMap>`: mapping record details. |

### createOrUpdate Create or update

| Item | Content |
| --- | --- |
| URL | `POST /api/app/view/createOrUpdate` |
| Params | `form`: body, `AppViewMap` entity; non-empty `id` updates, empty `id` creates; field pre-processing (`after`) runs before saving. |
| Returns | `ApiResult<AppViewMap>`: the saved mapping record. |

### isDelete Delete mapping

| Item | Content |
| --- | --- |
| URL | `DELETE /api/app/view/isDelete/{id}` |
| Params | `id`: path, string, mapping record ID. |
| Returns | `ApiResult<NullResult>`: `data` is empty. |

## BizDataController — `/bizData`

Batch transfer of business data ownership: rewrites creator and other ownership fields across entities.

### changeOwner Batch transfer ownership

| Item | Content |
| --- | --- |
| URL | `POST /api/bizData/changeOwner` |
| Params | `entityIdMap`: body, `Map<String,List<String>>`, keys are entity names, values are the lists of primary key IDs to transfer for each entity.<br />`owner`: query, string, required, target owner user ID. |
| Returns | `ApiResult<Map<String,Integer>>`: `data` maps each entity to the number of updated records. Admins can transfer any non-platform entity's data; non-admins can only transfer records they created, and the entity must contain ownership fields; platform entities (`platform` catalog/entity-name/table-name prefix) cannot be transferred; the whole request fails with deny details when unauthorized records, invalid entities, or an unknown user are involved. |

## DictController — `/dict`

Data dictionary maintenance; changes publish a dictionary upgrade event to refresh caches.

### pageQuery Paged query

| Item | Content |
| --- | --- |
| URL | `POST /api/dict/pageQuery` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiPagedResult`: `data` is a page of dictionaries (`Dict`). |

### query List query

| Item | Content |
| --- | --- |
| URL | `GET /api/dict/query` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiResult<List<Dict>>`: list of dictionaries. |

### get Get details

| Item | Content |
| --- | --- |
| URL | `GET /api/dict/get/{id}` |
| Params | `id`: path, string, dictionary ID. |
| Returns | `ApiResult<Dict>`: dictionary details. |

### createOrUpdate Create or update

| Item | Content |
| --- | --- |
| URL | `POST /api/dict/createOrUpdate` |
| Params | `form`: body, `Dict` entity; non-empty `id` updates, empty `id` creates. |
| Returns | `ApiResult<Dict>`: the saved dictionary; a dictionary upgrade event is published after saving. |

### isDelete Delete dictionary

| Item | Content |
| --- | --- |
| URL | `DELETE /api/dict/isDelete/{id}` |
| Params | `id`: path, string, dictionary ID. |
| Returns | `ApiResult<NullResult>`: `data` is empty; a dictionary upgrade event is published after deletion. |

### validate Validate code availability

| Item | Content |
| --- | --- |
| URL | `POST /api/dict/validate` |
| Params | `form`: body, `Dict` entity, provides the `dictCode`, `id`, `appId`, `tenantCode` to validate. |
| Returns | `ApiResult<Boolean>`: `true` means `dictCode` is available within the app. |

## DictItemController — `/dict/item`

Dictionary item maintenance and tree queries; changes publish a dictionary upgrade event.

### pageQuery Paged query

| Item | Content |
| --- | --- |
| URL | `POST /api/dict/item/pageQuery` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiPagedResult`: `data` is a page of dictionary items (`DictItem`). |

### query List query

| Item | Content |
| --- | --- |
| URL | `GET /api/dict/item/query` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiResult<List<DictItem>>`: list of dictionary items. |

### get Get details

| Item | Content |
| --- | --- |
| URL | `GET /api/dict/item/get/{id}` |
| Params | `id`: path, string, dictionary item ID. |
| Returns | `ApiResult<DictItem>`: dictionary item details. |

### createOrUpdate Create or update

| Item | Content |
| --- | --- |
| URL | `POST /api/dict/item/createOrUpdate` |
| Params | `form`: body, `DictItem` entity; non-empty `id` updates, empty `id` creates. |
| Returns | `ApiResult<DictItem>`: the saved dictionary item; a dictionary upgrade event is published after saving. |

### batchCreateOrUpdate Batch save

| Item | Content |
| --- | --- |
| URL | `POST /api/dict/item/batchCreateOrUpdate` |
| Params | `forms`: body, `List<DictItem>`, list of dictionary items.<br />`dictId`: query, string, dictionary ID.<br />`parentId`: query, string, parent item ID. |
| Returns | `ApiResult<NullResult>`: `data` is empty; batch saves the dictionary items under the dictionary (parent item) and publishes a dictionary upgrade event on success. |

### isDelete Delete dictionary item

| Item | Content |
| --- | --- |
| URL | `DELETE /api/dict/item/isDelete/{id}` |
| Params | `id`: path, string, dictionary item ID. |
| Returns | `ApiResult<NullResult>`: `data` is empty; the item is deleted and set to disabled, and a dictionary upgrade event is published. |

### queryItemByDictCode Query item tree by code

| Item | Content |
| --- | --- |
| URL | `GET /api/dict/item/queryItemByDictCode/{dictCode}` |
| Params | `dictCode`: path, string, dictionary code. |
| Returns | `ApiResult<List<DictItem>>`: `data` is the tree of enabled dictionary items under the dictionary (items with an empty `pid` are roots; children hang off `children`). |

### validate Validate code availability

| Item | Content |
| --- | --- |
| URL | `POST /api/dict/item/validate` |
| Params | `form`: body, `DictItem` entity, provides the `itemCode`, `id`, `dictId`, `tenantCode` to validate. |
| Returns | `ApiResult<Boolean>`: `true` means `itemCode` is available within the dictionary. |

### createDictAndItems Create dictionary and items

| Item | Content |
| --- | --- |
| URL | `POST /api/dict/item/createDictAndItems` |
| Params | `form`: body, `Dict` entity, whose `dictItems` field carries the dictionary item list (incoming item IDs and `seqNo` are ignored; `seqNo` is generated by list order). |
| Returns | `ApiResult<Dict>`: `data` is the newly created dictionary (including its generated items); a dictionary upgrade event is published on success. |

### queryItemTagsByDict Query item tags

| Item | Content |
| --- | --- |
| URL | `GET /api/dict/item/queryItemTagsByDict/{dictString}` |
| Params | `dictString`: path, string, dictionary ID or dictionary code. |
| Returns | `ApiResult<Set<String>>`: `data` is the set of tags from all enabled dictionary items of the dictionary (`itemTag` split by comma and de-duplicated); empty when the dictionary does not exist. |

## ScheduleController ⚙ — `/schedule`

Scheduled task management (design-time): query, maintenance, code validation, and enable/disable.

### pageQuery Paged query

| Item | Content |
| --- | --- |
| URL | `POST /api/schedule/pageQuery` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiPagedResult`: `data` is a page of scheduled tasks (`Schedule`). |

### query List query

| Item | Content |
| --- | --- |
| URL | `GET /api/schedule/query` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiResult<List<Schedule>>`: list of scheduled tasks. |

### get Get details

| Item | Content |
| --- | --- |
| URL | `GET /api/schedule/get/{id}` |
| Params | `id`: path, string, task ID. |
| Returns | `ApiResult<Schedule>`: scheduled task details. |

### createOrUpdate Create or update

| Item | Content |
| --- | --- |
| URL | `POST /api/schedule/createOrUpdate` |
| Params | `form`: body, `Schedule` entity; non-empty `id` updates, empty `id` creates; when creating a task of type `JAVA` the task `code` is generated automatically. |
| Returns | `ApiResult<Schedule>`: the saved scheduled task. |

### isDelete Delete task

| Item | Content |
| --- | --- |
| URL | `DELETE /api/schedule/isDelete/{id}` |
| Params | `id`: path, string, task ID. |
| Returns | `ApiResult<NullResult>`: `data` is empty. |

### validate Validate code availability

| Item | Content |
| --- | --- |
| URL | `POST /api/schedule/validate` |
| Params | `form`: body, `Schedule` entity, provides the `code`, `id`, `tenantCode` to validate. |
| Returns | `ApiResult<Boolean>`: `true` means `code` is available. |

### status Change status

| Item | Content |
| --- | --- |
| URL | `GET /api/schedule/status/{id}/{status}` |
| Params | `id`: path, string, task ID.<br />`status`: path, int, target status. |
| Returns | `ApiResult<Schedule>`: the scheduled task after the status update. |

## ScheduleLogController ⚙ — `/schedule/log`

Query and maintenance of scheduled task execution logs (design-time).

### pageQuery Paged query

| Item | Content |
| --- | --- |
| URL | `POST /api/schedule/log/pageQuery` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiPagedResult`: `data` is a page of task logs (`ScheduleLog`). |

### query List query

| Item | Content |
| --- | --- |
| URL | `GET /api/schedule/log/query` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiResult<List<ScheduleLog>>`: list of task logs. |

### get Get details

| Item | Content |
| --- | --- |
| URL | `GET /api/schedule/log/get/{id}` |
| Params | `id`: path, string, log ID. |
| Returns | `ApiResult<ScheduleLog>`: task log details. |

### createOrUpdate Create or update

| Item | Content |
| --- | --- |
| URL | `POST /api/schedule/log/createOrUpdate` |
| Params | `form`: body, `ScheduleLog` entity; non-empty `id` updates, empty `id` creates. |
| Returns | `ApiResult<ScheduleLog>`: the saved task log. |

### isDelete Delete log

| Item | Content |
| --- | --- |
| URL | `DELETE /api/schedule/log/isDelete/{id}` |
| Params | `id`: path, string, log ID. |
| Returns | `ApiResult<NullResult>`: `data` is empty. |

## SqlController ⚙ — `/sql`

Custom SQL management (design-time): SQL maintenance, key validation, and script cache refresh.

### pageQuery Paged query

| Item | Content |
| --- | --- |
| URL | `POST /api/sql/pageQuery` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiPagedResult`: `data` is a page of custom SQL (`CustomSql`). |

### query List query

| Item | Content |
| --- | --- |
| URL | `GET /api/sql/query` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiResult<List<CustomSql>>`: list of custom SQL. |

### get Get details

| Item | Content |
| --- | --- |
| URL | `GET /api/sql/get/{id}` |
| Params | `id`: path, string, SQL ID. |
| Returns | `ApiResult<CustomSql>`: custom SQL details. |

### createOrUpdate Create or update

| Item | Content |
| --- | --- |
| URL | `POST /api/sql/createOrUpdate` |
| Params | `form`: body, `CustomSql` entity; non-empty `id` updates, empty `id` creates; field pre-processing (`afterSet`) runs before saving. |
| Returns | `ApiResult<CustomSql>`: the saved custom SQL. |

### isDelete Delete SQL

| Item | Content |
| --- | --- |
| URL | `DELETE /api/sql/isDelete/{id}` |
| Params | `id`: path, string, SQL ID. |
| Returns | `ApiResult<NullResult>`: `data` is empty. |

### validate Validate key availability

| Item | Content |
| --- | --- |
| URL | `POST /api/sql/validate` |
| Params | `form`: body, `CustomSql` entity, provides the `keyName`, `id`, `appId`, `tenantCode` to validate. |
| Returns | `ApiResult<Boolean>`: `true` means `keyName` is available. |

### refresh Refresh SQL cache

| Item | Content |
| --- | --- |
| URL | `GET /api/sql/refresh/{sqlKey}` |
| Params | `sqlKey`: path, string, SQL script key. |
| Returns | `ApiResult<NullResult>`: `data` is empty; refreshes the registered SQL script cache by key. |

### queryByApp Query SQL by app

| Item | Content |
| --- | --- |
| URL | `GET /api/sql/query/{appId}` |
| Params | `appId`: path, string, app ID. |
| Returns | `ApiResult<List<CustomSql>>`: `data` is the merge of the app's own custom SQL and the SQL granted (enabled and approved) to the app by other apps. |

## SysConfigController — `/sys/config`

System configuration item management: config key-value maintenance, encrypted config reading, and environment cache refresh.

### pageQuery Paged query

| Item | Content |
| --- | --- |
| URL | `POST /api/sys/config/pageQuery` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiPagedResult`: `data` is a page of system configs (`SysConfig`); upload-type configs carry the attachment name (`configAssist`). |

### query List query

| Item | Content |
| --- | --- |
| URL | `GET /api/sys/config/query` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiResult<List<SysConfig>>`: list of system configs, also carrying `configAssist`. |

### get Get details

| Item | Content |
| --- | --- |
| URL | `GET /api/sys/config/get/{id}` |
| Params | `id`: path, string, config ID.<br />`encrypt`: query, boolean, optional, default `false`; when `true` and the config is encrypted, the decrypted value is returned. |
| Returns | `ApiResult<SysConfig>`: config details (the SM2 key field is never returned). |

### createOrUpdate Create or update

| Item | Content |
| --- | --- |
| URL | `POST /api/sys/config/createOrUpdate` |
| Params | `form`: body, `SysConfig` entity; non-empty `id` updates, empty `id` creates; field pre-processing (`afterSet`) runs before saving. |
| Returns | `ApiResult<SysConfig>`: the saved config; for enabled configs the environment config cache is refreshed after saving. |

### isDelete Delete config

| Item | Content |
| --- | --- |
| URL | `DELETE /api/sys/config/isDelete/{id}` |
| Params | `id`: path, string, config ID. |
| Returns | `ApiResult<NullResult>`: `data` is empty; the config is deleted and set to disabled. |

### validate Validate key availability

| Item | Content |
| --- | --- |
| URL | `POST /api/sys/config/validate` |
| Params | `form`: body, `SysConfig` entity, provides the `configKey`, `id`, `tenantCode` to validate. |
| Returns | `ApiResult<Boolean>`: `true` means `configKey` is available. |

### getValue Get value by key

| Item | Content |
| --- | --- |
| URL | `GET /api/sys/config/getValue/{key}` |
| Params | `key`: path, string, config key. |
| Returns | `ApiResult<String>`: `data` is the value of the enabled config; an empty string when nothing matches. |

## TreeNodeController — `/treeNode`

Query and deletion of tree nodes (`TreeNode`).

### query List query

| Item | Content |
| --- | --- |
| URL | `GET /api/treeNode/query` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiResult<List<TreeNode>>`: list of tree nodes. |

### isDelete Delete node

| Item | Content |
| --- | --- |
| URL | `DELETE /api/treeNode/isDelete/{id}` |
| Params | `id`: path, string, node ID. |
| Returns | `ApiResult<NullResult>`: `data` is empty. |
