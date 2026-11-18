---
title: API · ORM Hooks
sidebar_label: ORM Hooks
---
# ORM Hooks (ormhook)

1 controller with 7 endpoints in total. ORM entity hooks are a purely additive feature: after the transaction of any entity insert/update/delete in the platform commits, the full payload JSON is POSTed to the hook URL and the downstream system dispatches by `entityName`/`eventType`; triggering and execution are fully asynchronous, retried/dead-lettered through the outbox (`platform_orm_hook_log`), and failures never propagate to the business path. This domain covers hook configuration CRUD, execution outbox monitoring and dead-letter replay.

## OrmHookController — `/ormhook`

Hook configuration CRUD, execution outbox monitoring and dead-letter replay; the controller contains no try-catch — exceptions propagate to the global exception handler and are converted into ApiResult.

### pageQuery Page query hook configs

| Item | Content |
| --- | --- |
| URL | `POST /api/ormhook/pageQuery` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiPagedResult`: paged list of hook configurations (declared return type is the raw `ApiPagedResult`). |

### query List hook configs

| Item | Content |
| --- | --- |
| URL | `GET /api/ormhook/query` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiResult<List<OrmHook>>`: full array of hook configurations (designer entity configuration page contract: GET returns the full array). |

### get Hook config details

| Item | Content |
| --- | --- |
| URL | `GET /api/ormhook/get/{id}` |
| Params | `id`: path, string, required, hook configuration ID. |
| Returns | `ApiResult<OrmHook>`: a single hook configuration; fails when it does not exist. |

### createOrUpdate Create or update hook

| Item | Content |
| --- | --- |
| URL | `POST /api/ormhook/createOrUpdate` |
| Params | `form`: body, OrmHook object, required, common fields: hook name `title` (required), downstream receiver URL `httpUrl` (required, must be a valid http/https URL), enabled flag `enableStatus` (0 disabled, 1 enabled, default 1), `id` (empty = create, non-empty = update). |
| Returns | `ApiResult<OrmHook>`: hard validation on write (missing title/URL or an invalid URL is rejected outright with the reason), takes effect immediately after save (refreshes the hook rule snapshot) and returns the saved configuration. |

### isDelete Delete hook config

| Item | Content |
| --- | --- |
| URL | `DELETE /api/ormhook/isDelete/{id}` |
| Params | `id`: path, string, required, hook configuration ID. |
| Returns | `ApiResult<Boolean>`: disables first, then logically deletes; takes effect immediately and returns `true` on success. |

### logPageQuery Outbox page query

| Item | Content |
| --- | --- |
| URL | `POST /api/ormhook/log/pageQuery` |
| Params | Pagination and filter params: see [Conventions](../reference.md) (execution records can be filtered by `status`/`entityName`/`hookId`) |
| Returns | `ApiPagedResult`: paged list of the execution outbox (`platform_orm_hook_log`; declared return type is the raw `ApiPagedResult`). |

### replay Replay dead letters

| Item | Content |
| --- | --- |
| URL | `POST /api/ormhook/log/replay/{id}` |
| Params | `id`: path, string, required, outbox record ID. |
| Returns | `ApiResult<Boolean>`: resets a dead record to ready (retry count cleared, error cleared, and an in-memory timer registered for immediate pickup); returns `false` when the record does not exist or is not in dead status. |
