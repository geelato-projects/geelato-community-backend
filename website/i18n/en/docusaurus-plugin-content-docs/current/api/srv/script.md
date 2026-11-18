---
title: API · Script & External Services
sidebar_label: Script & External Services
---
# Script & External Services (script)

5 controllers, 16 endpoints in total. All endpoints in this domain are design-time APIs.

## ApiController ⚙ — `/script/api`

Definition, maintenance, query and code validation of script APIs (`Api`, table platform_api).

### pageQuery Paged query

| Item | Content |
| --- | --- |
| URL | `POST /api/script/api/pageQuery` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiPagedResult`: `data` is a page of script APIs (`Api`). |

### query List query

| Item | Content |
| --- | --- |
| URL | `GET /api/script/api/query` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiResult<List<Api>>`: list of script APIs. |

### queryGroupName Group name list

| Item | Content |
| --- | --- |
| URL | `GET /api/script/api/queryGroupName` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiResult<List<String>>`: deduplicated `groupName` values from the query result (blank values excluded). |

### get Single record

| Item | Content |
| --- | --- |
| URL | `GET /api/script/api/get/{id}` |
| Params | `id`: path, string, script API primary key, required. |
| Returns | `ApiResult<Api>`: `data` is a single script API, with `requestParams` and `responseParams` attached by alternate type (`REQUEST`/`RESPONSE`). |

### createOrUpdate Create or update

| Item | Content |
| --- | --- |
| URL | `POST /api/script/api/createOrUpdate` |
| Params | `form`: body, `Api` entity; blank `id` creates, non-blank updates. Fields referenced in code include `id`, `code`, `appId`, `tenantCode`, `groupName`, `outsideUrl`, `requestParams`, `responseParams`; see the entity for the rest. |
| Returns | `ApiResult<Api>`: `data` is the created or updated script API. |

### isDelete Soft delete

| Item | Content |
| --- | --- |
| URL | `DELETE /api/script/api/isDelete/{id}` |
| Params | `id`: path, string, script API primary key, required. |
| Returns | `ApiResult<NullResult>`: `data` is empty; fails if the record does not exist. |

### validate Code uniqueness check

| Item | Content |
| --- | --- |
| URL | `POST /api/script/api/validate` |
| Params | `form`: body, `Api` entity, only `id`, `code`, `appId`, `tenantCode` are used. |
| Returns | `ApiResult<Boolean>`: checks whether the `code` is available in table platform_api (excludes the record itself by `id`, counts only non-deleted records, falls back to the session tenant when `tenant_code` is blank); `true` means no conflict. |

### generateOutside Generate outside URL

| Item | Content |
| --- | --- |
| URL | `POST /api/script/api/generateOutside` |
| Params | `form`: body, `Api` entity, only `id` is used (its own `outsideUrl` is excluded from the duplicate check). |
| Returns | `ApiResult<String>`: `data` is a random 10-character path starting with `/`, guaranteed unique against existing `outsideUrl` values. |

## ApiParamController ⚙ — `/script/api/param`

Query and maintenance of script API params (`ApiParam`).

### pageQuery Paged query

| Item | Content |
| --- | --- |
| URL | `POST /api/script/api/param/pageQuery` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiPagedResult`: `data` is a page of script API params (`ApiParam`). |

### query List query

| Item | Content |
| --- | --- |
| URL | `GET /api/script/api/param/query` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiResult`: `data` is a list of script API params (`ApiParam`) (the method signature declares no generic; noted by actual response). |

### get Single record

| Item | Content |
| --- | --- |
| URL | `GET /api/script/api/param/get/{id}` |
| Params | `id`: path, string, param primary key, required. |
| Returns | `ApiResult`: `data` is a single script API param (`ApiParam`). |

### createOrUpdate Create or update

| Item | Content |
| --- | --- |
| URL | `POST /api/script/api/param/createOrUpdate` |
| Params | `form`: body, `ApiParam` entity; blank `id` creates, non-blank updates. |
| Returns | `ApiResult`: `data` is the created or updated script API param. |

### isDelete Soft delete

| Item | Content |
| --- | --- |
| URL | `DELETE /api/script/api/param/isDelete/{id}` |
| Params | `id`: path, string, param primary key, required. |
| Returns | `ApiResult<NullResult>`: `data` is empty; fails if the record does not exist. |

## ExtServiceController ⚙ — `/ext`

Unified entry point for published script APIs: matches and executes a script by its outside URL. The endpoint is annotated with `@IgnoreVerify` to skip platform API verification.

### exec Execute script by outside URL

| Item | Content |
| --- | --- |
| URL | `GET\|POST /api/ext/{outside_url}` |
| Params | `outside_url`: path, string, the outside access URL of the script API (maps to `Api.outsideUrl`).<br />body: string, raw request body text, passed to the script as its input (empty for GET requests). |
| Returns | `ApiResult`: `data` is the script execution result; if the script API sets `responseFormat=custom`, the result JSON string is returned directly (not wrapped in `ApiResult`). Returns failure info on execution error; a debug log is written to platform_api_log when the API `logLevel` contains `dg`. |

## GraalServiceController ⚙ — `/script/function`

Exposes descriptions of functions registered in GraalVM.

### getGraalDescriptions Function description list

| Item | Content |
| --- | --- |
| URL | `GET /api/script/function/list` |
| Params | None |
| Returns | `ApiResult`: `data` is the list of GraalVM function service descriptions (from GraalManager registration info). |

## ServiceController ⚙ — `/service`

Internal script execution entry point: executes a script by script ID.

### exec Execute script by ID

| Item | Content |
| --- | --- |
| URL | `POST /api/service/exec/{scriptId}` |
| Params | `scriptId`: path, string, internal script ID, required.<br />body: string, raw request body text, passed to the script as its input. |
| Returns | `ApiResult`: `data` is the script execution result; returns failure info on execution error. |
