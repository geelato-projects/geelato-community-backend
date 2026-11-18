---
title: API · Metadata
sidebar_label: Metadata
---
# Metadata (meta)

2 controllers with 26 endpoints in total. Endpoints marked with ⚙ are design-time APIs. MetaController merges the former runtime and design-time controllers under the same prefix `/api/meta`; its runtime endpoints read the GQL query body directly from the raw request (the body is not a plain JSON map). See [MQL](../../mql/overview.md) for GQL/MQL semantics.

## MetaController — `/meta`

Metadata service under a single prefix: runtime APIs (GQL query/save/delete) and design-time APIs (entity metadata definitions, conflict diagnosis/sync between Java class sources and the online DB source, reverse code generation).

### list GQL list query

| Item | Content |
| --- | --- |
| URL | `POST\|GET /api/meta/list`, `POST\|GET /api/meta/list/*` |
| Params | `withMeta`: query, boolean, optional, default `true`, whether to include field metadata with the data.<br />GQL query body (read from the raw request): a single entity object or an array of entities; pagination (`@p`), fields (`@fs`), view template params (`@pf`) and more are expressed in GQL, see [MQL](../../mql/overview.md). |
| Returns | `ApiPagedResult`: `data` is a list of record maps; `page`/`size`/`dataSize`/`total` are filled from the query result, and `meta`/`cache` are returned when present (declared return type is the raw `ApiPagedResult<?>`). |

### multiList GQL multi-entity query

| Item | Content |
| --- | --- |
| URL | `POST /api/meta/multiList` |
| Params | `withMeta`: query, boolean, optional, default `true`, whether to include field metadata with the data.<br />GQL query body (read from the raw request): an array of entities, each with its own pagination and filters. |
| Returns | `ApiMultiPagedResult`: `data` is a `Map<entityName, PageData>`; each PageData holds that entity's `data`/`total`/`page`/`size`/`dataSize`/`meta`; the top-level `cache` flag is returned when present. |

### save GQL save one entity

| Item | Content |
| --- | --- |
| URL | `POST /api/meta/save/{biz}` |
| Params | `biz`: path, string, required, business entity name.<br />GQL save body (read from the raw request). |
| Returns | `ApiMetaResult`: `data` is the primary key of the saved record (string); on failure a marker such as `saveFail`/`transactionRollback` is returned. |

### batchSave GQL batch save

| Item | Content |
| --- | --- |
| URL | `POST /api/meta/batchSave` |
| Params | GQL batch save body (read from the raw request): multiple save commands executed one by one within a single transaction. |
| Returns | `ApiMetaResult`: `data` is the list of saved primary keys (string array); any failure rolls back the whole batch. |

### multiSave GQL multi-entity save

| Item | Content |
| --- | --- |
| URL | `POST /api/meta/multiSave` |
| Params | GQL save body (read from the raw request): multi-entity save commands executed within a single transaction. |
| Returns | `ApiMetaResult`: `data` is the list of saved primary keys (string array). |

### delete Delete by primary key

| Item | Content |
| --- | --- |
| URL | `POST /api/meta/delete/{biz}/{id}` |
| Params | `biz`: path, string, required, business entity name.<br />`id`: path, string, required, record primary key. |
| Returns | `ApiResult<Integer>`: number of deleted records. |

### deleteByGql Delete by GQL condition

| Item | Content |
| --- | --- |
| URL | `POST /api/meta/delete2/{biz}` |
| Params | `biz`: path, string, required, business entity name.<br />GQL delete body (read from the raw request): delete conditions are expressed in GQL. |
| Returns | `ApiResult<Integer>`: number of deleted records. |

### treeNodeList Tree node list

| Item | Content |
| --- | --- |
| URL | `POST /api/meta/tree/{biz}` |
| Params | `entity`: query, string, required, business entity name associated with `platform_tree_node` (the entity must have a `tree_node_id` field).<br />`treeId`: query, long, required, tree ID.<br />`biz`: path, string, path placeholder (unused in the method body). |
| Returns | `ApiResult`: tree node list (children nested under `children`); fails with the reason when the entity is not valid. |

### uniqueness Uniqueness check

| Item | Content |
| --- | --- |
| URL | `POST\|GET /api/meta/uniqueness` |
| Params | GQL query body (read from the raw request): the check condition is expressed in GQL; `@fs=id` is added automatically when `@fs` is absent, and `@p=1,10` when `@p` is absent. |
| Returns | `ApiResult<Boolean>`: `true` when the check query total is 0 (no duplicates; declared return type is `ApiResult<?>`). |

### defined Entity field metadata

| Item | Content |
| --- | --- |
| URL | `POST\|GET /api/meta/defined/{entity}` |
| Params | `entity`: path, string, required, entity name. |
| Returns | `ApiMetaResult`: `data` is all simple field metadata of the entity; fails with `not found meta defined` when the entity does not exist. |

### fullDefined Full entity metadata

| Item | Content |
| --- | --- |
| URL | `POST\|GET /api/meta/fullDefined/{entity}` |
| Params | `entity`: path, string, required, entity name. |
| Returns | `ApiMetaResult`: `data` is the complete metadata of the entity (EntityMeta). |

### entityNames Entity name list

| Item | Content |
| --- | --- |
| URL | `POST\|GET /api/meta/entityNames` |
| Params | None |
| Returns | `ApiResult`: list of all loaded entity names. |

### queryLiteEntities Lite entity metadata

| Item | Content |
| --- | --- |
| URL | `POST\|GET /api/meta/entityLiteMetas` |
| Params | None |
| Returns | `ApiResult`: list of lite entity metadata. |

### conflicts Conflicting entities

| Item | Content |
| --- | --- |
| URL | `POST\|GET /api/meta/conflicts` |
| Params | None |
| Returns | `ApiResult`: `data` is a `Map<entityName, diff details>` listing entities that exist in both the Java class source and the online DB source with field-level diffs (for diagnosing duplicated entities that break CRUD SQL generation). |

### conflict Entity conflict details

| Item | Content |
| --- | --- |
| URL | `POST\|GET /api/meta/conflicts/{entity}` |
| Params | `entity`: path, string, required, entity name. |
| Returns | `ApiResult`: field-level diff details of the entity; an empty diff is returned when there is no conflict. |

### sync Sync entity metadata

| Item | Content |
| --- | --- |
| URL | `POST /api/meta/sync/{entity}` |
| Params | `entity`: path, string, required, entity name.<br />`direction`: query, string, optional, default `db-to-class`; currently the only supported value (any other value fails immediately). |
| Returns | `ApiResult`: reloads the entity's online DB definition, refreshes the main cache (db-to-class direction) and returns the latest diff verification result. |

### codegen Reverse-generate source code

| Item | Content |
| --- | --- |
| URL | `POST\|GET /api/meta/codegen/{entity}` |
| Params | `entity`: path, string, required, entity name.<br />`packageName`: query, string, optional, package of the generated class, default `cn.geelato.meta`. |
| Returns | `ApiResult<Map<String,Object>>`: `className`, `fileName`, `packageName`, `source` (.java source text annotated with `@Entity`/`@Col`/`@Title`/`@Id`; preview only — nothing is persisted to the DB or written to disk). |

### codegenDownload Download generated source

| Item | Content |
| --- | --- |
| URL | `GET /api/meta/codegen/{entity}/download` |
| Params | `entity`: path, string, required, entity name.<br />`packageName`: query, string, optional, default `cn.geelato.meta`. |
| Returns | Writes directly to the response stream (no JSON body): streams the generated `{ClassName}.java` source file as `text/plain`; returns 404 when the entity metadata does not exist. |

## MetaDdlController ⚙ — `/meta/ddl`

Design-time DDL operations: recreate database tables and views per entity/app, validate view SQL, refresh the online metadata cache.

### recreate Recreate table

| Item | Content |
| --- | --- |
| URL | `POST /api/meta/ddl/table/{entity}` |
| Params | `entity`: path, string, required, entity name (determines the database table to operate on). |
| Returns | `ApiMetaResult`: recreates or creates the database table by entity name (requires switching the database); `data` is empty on success, error message on failure. |

### createOrUpdateTableByAppId Recreate tables by app

| Item | Content |
| --- | --- |
| URL | `POST /api/meta/ddl/tables/{appId}` |
| Params | `appId`: path, string, required, app ID (determines the scope; tenant code comes from the current session). |
| Returns | `ApiMetaResult`: `data` is a `Map<entityName, result>` (`true` created/updated, `false` failed, `"ignore"` skipped for non-manual source types); `data` is empty when the app has no enabled tables; on error `data` holds the partial results plus an error message. |

### createOrUpdateViewByAppId Recreate views by app

| Item | Content |
| --- | --- |
| URL | `POST /api/meta/ddl/views/{appId}` |
| Params | `appId`: path, string, required, app ID (tenant code comes from the current session). |
| Returns | `ApiMetaResult`: `data` is a `Map<viewName, result>` (`true` created/updated; explanatory text for virtual/materialized views or missing related connection/table); `data` is empty when the app has no views. |

### createOrUpdateViewById Recreate view by ID

| Item | Content |
| --- | --- |
| URL | `POST /api/meta/ddl/viewOne/{id}` |
| Params | `id`: path, string, required, view ID. |
| Returns | `ApiMetaResult`: creates or updates a single view (virtual and materialized views are rejected); `data` is empty on success. |

### createOrUpdateViewByEntity Create view from SQL

| Item | Content |
| --- | --- |
| URL | `POST /api/meta/ddl/view/{view}` |
| Params | `view`: path, string, required, view name.<br />`connectId`: body, string, required, database connection ID.<br />`sql`: body, string, required, view SQL (body is a JSON object with `connectId` and `sql`). |
| Returns | `ApiMetaResult`: validates the SQL first, then creates/updates the view (requires switching the database); `data` is empty on success. |

### validateView Validate view SQL

| Item | Content |
| --- | --- |
| URL | `POST /api/meta/ddl/view/valid/{connectId}` |
| Params | `connectId`: path, string, required, database connection ID.<br />`sql`: body, string, required, view SQL to validate (body is a JSON object, `sql` field is read). |
| Returns | `ApiMetaResult<Boolean>`: whether the view SQL is valid (on validation exceptions the success structure is still returned with `data` of `false` plus the error message). |

### validateView Validate views by entity

| Item | Content |
| --- | --- |
| URL | `POST /api/meta/ddl/view/valid/{connectId}/{entityName}` |
| Params | `connectId`: path, string, required, database connection ID.<br />`entityName`: path, string, required, entity name. |
| Returns | `ApiMetaResult<Map<String,String>>`: `data` is a `Map<viewName, result>` (`"true"` valid, `"false"` invalid or the exception message); an empty map when no views match. |

### refreshRedis Refresh metadata cache

| Item | Content |
| --- | --- |
| URL | `POST /api/meta/ddl/redis/refresh` |
| Params | `tableId`: body, string, table ID.<br />`entityName`: body, string, entity name.<br />`connectId`: body, string, connection ID.<br />`appId`: body, string, app ID.<br />`tenantCode`: body, string, optional, tenant code (falls back to the current session tenant when absent). (body is a JSON object) |
| Returns | `ApiMetaResult`: re-parses the online DB metadata by the given conditions and refreshes the cache (operates only on the metadata database); `data` is empty on success. |
