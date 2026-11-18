---
title: API · Platform Basics
sidebar_label: Platform Basics
---
# Platform Basics (base)

15 controllers with 46 endpoints in total. Endpoints marked with ⚙ are design-time APIs.

## AuxiliarySuiteHealthController ⚙ — `/system/monitor/auxiliary-suites`

Health status query and manual refresh for auxiliary suites, admin-only.

### health Health status query

| Item | Content |
| --- | --- |
| URL | `GET /api/system/monitor/auxiliary-suites/health` |
| Params | None |
| Returns | `ApiResult<AuxiliarySuiteHealthSummary>`: auxiliary suite health summary (checked time, poll interval in seconds, suite count with healthy/abnormal/unknown counts, `hasFailure` flag, last error, per-suite snapshot list `suites`); fails for non-admin users. |

### refresh Immediate health refresh

| Item | Content |
| --- | --- |
| URL | `GET、POST /api/system/monitor/auxiliary-suites/refresh` |
| Params | None |
| Returns | `ApiResult<AuxiliarySuiteHealthSummary>`: latest summary after running a health check immediately; fails for non-admin users. |

## CacheController — `/cache`

Key listing, removal, and clearing for J2Cache, plus its runtime configuration.

### remove Remove cache keys

| Item | Content |
| --- | --- |
| URL | `GET、POST /api/cache/remove/{keys}` |
| Params | `keys`: path, string, required, comma-separated cache keys, removed one by one (blank entries skipped); fails when empty. |
| Returns | `ApiResult<NullResult>`: `data` is empty. |

### config Cache configuration view

| Item | Content |
| --- | --- |
| URL | `GET /api/cache/config` |
| Params | None |
| Returns | `ApiResult<Map<String,Object>>`: `l1`/`l2` (enabled flag and provider; `l2.config` includes redis mode, hosts, namespace, database, with the password masked), `config` (`open_spring_cache`, `clean_on_start`, `broadcast`, `serialization`, `default_cache_expire`), `capabilities` (`channel_present`, `clear_region_supported`, `evict_supported`). |

### listL1 L1 cache list

| Item | Content |
| --- | --- |
| URL | `GET /api/cache/list/l1` |
| Params | `keyword`: query, string, optional, fuzzy filter on cache keys (case-insensitive). |
| Returns | `ApiResult<List<Map<String,Object>>>`: level-1 (L1) cache entries, each with `key`, `type` (`L1`), `region`, `size` (UTF-8 bytes), `createTime` (always null), `expireTime` (null). |

### listL2 L2 cache list

| Item | Content |
| --- | --- |
| URL | `GET /api/cache/list/l2` |
| Params | `keyword`: query, string, optional, fuzzy filter on cache keys (case-insensitive). |
| Returns | `ApiResult<List<Map<String,Object>>>`: level-2 (L2) cache entries with the same fields as `listL1` but `type` of `L2`; `expireTime` is derived from the Redis TTL (only available in redis `single` mode, otherwise null). |

### get Get cache value

| Item | Content |
| --- | --- |
| URL | `GET /api/cache/get/{key}` |
| Params | `key`: path, string, required, cache key; fails when empty. |
| Returns | `ApiResult<Object>`: `data` is the cached value (fetched reflectively via `CacheUtil` `get`/`getValue`, null when absent). |

### clearAll Clear caches

| Item | Content |
| --- | --- |
| URL | `GET、POST /api/cache/clear` |
| Params | None |
| Returns | `ApiResult<NullResult>`: clears the `default` region (`channel.clear`), falling back to per-key removal when the channel is unavailable. `data` is empty. |

## ConfigController — `/config`

Platform system configuration query (grouped by platform/tenant/app) and refresh of a given config key.

### list Platform configuration query

| Item | Content |
| --- | --- |
| URL | `GET /api/config` |
| Params | `tenantCode`: query, string, optional, emits the `tenant` group when tenant-level configs match.<br />`appId`: query, string, optional, emits the `app` group when app-level configs match. |
| Returns | `ApiResult<Map<String,Object>>`: grouped key-value pairs: `platform` (global), `tenant` (given tenant), `app` (given app), merged from the `webapp` and `all` config purposes. |

### refresh Refresh config key

| Item | Content |
| --- | --- |
| URL | `GET /api/config/refresh/{configKey}` |
| Params | `configKey`: path, string, required, config key. |
| Returns | `ApiResult<NullResult>`: refreshes the given config via `EnvManager`, `data` is empty; returns a failure message on exception. |

## DictionaryController — `/system/dictionary`

Query enabled dictionary items by dictionary code.

### getByCode Dictionary items by code

| Item | Content |
| --- | --- |
| URL | `GET /api/system/dictionary/{code}` |
| Params | `code`: path, string, dictionary code; returns an empty list when blank. |
| Returns | `ApiResult<List<DictItem>>`: enabled, non-deleted dictionary items ordered by `seqNo` ascending; empty list when the dictionary does not exist. |

## DownloadController — `/resources`

Attachment file download and site configuration file reading.

### downloadFile Download attachment

| Item | Content |
| --- | --- |
| URL | `GET /api/resources/file` |
| Params | `id`: query, string, attachment ID.<br />`isPdf`: query, boolean, convert to PDF before download (requires the aspose dependency on the host).<br />`isPreview`: query, boolean, preview mode.<br />`isThumbnail`: query, boolean, download the thumbnail.<br />`name`, `path`: query, string, declared in the method signature but not used by the current handler logic. |
| Returns | Writes the response stream directly (no JSON body): fetches the attachment by ID (from OSS or the local path) and streams it under the original file name; with `isPdf=true` it converts to PDF and streams under a PDF file name; responses carry `Cache-Control`, `ETag`, and `Last-Modified` headers. |

### downloadJson Read site config

| Item | Content |
| --- | --- |
| URL | `GET /api/resources/json` |
| Params | `fileName`: query, string, required, config file name; the `.config` suffix is appended automatically when missing. |
| Returns | `ApiResult<String>`: `data` is the full site configuration file content (UTF-8); fails when the file does not exist. |

## IDGenerateController — `/id`

Generate a globally unique ID.

### generate Generate unique ID

| Item | Content |
| --- | --- |
| URL | `GET /api/id/generate` |
| Params | None |
| Returns | `ApiResult<Long>`: `data` is a unique ID generated by `UIDGenerator.generate()` (long). |

## OrganizationController — `/org`

Query an organization record by organization ID.

### getOrgById Organization by ID

| Item | Content |
| --- | --- |
| URL | `GET /api/org/{orgId}` |
| Params | `orgId`: path, string, organization ID. |
| Returns | Not wrapped in `ApiResult`; returns the JSON object `Map<String,Object>` directly: `{success: true, data: <organization record>}` (a full `platform_org` row) on success, `{success: false, message: <reason>}` on failure. |

## PageCheckController — `/page/check`

Page check-out/check-in and page takeover request (check request) workflow.

### checkPage Page check-out/check-in

| Item | Content |
| --- | --- |
| URL | `POST /api/page/check` |
| Params | `pageId`: body, string, required, page ID.<br />`action`: body, string, required, `checkout` / `checkin`.<br />`force`: body, boolean, optional, force check-in when the page is held by another user (`checkin` only; requires app admin permission). |
| Returns | `ApiResult<Map<String,Object>>`: `data` contains `checkStatus`, `checkUserId`, `checkUserName`, `checkAt`; each check-out/check-in branch returns its own success or failure message. |

### getCheckedOutPageList Checked-out page list

| Item | Content |
| --- | --- |
| URL | `GET /api/page/check/list` |
| Params | `appId`: query, string, required, app ID. |
| Returns | `ApiResult<List<Map<String,Object>>>`: checked-out, non-deleted pages of the app, each with `pageId`, `extendId`, `title`, `checkUserId`, `checkUserName`, `checkAt`, `lastUpdateTime`. |

### syncPageStatus Sync page status

| Item | Content |
| --- | --- |
| URL | `GET /api/page/check/sync` |
| Params | `pageId`: query, string, required, page ID. |
| Returns | `ApiResult<Map<String,Object>>`: page check status: `checkStatus`, `checkUserId`, `checkUserName`, `checkAt`, `version`, `lastUpdateTime`. |

### requestPageCheckOut Request page takeover

| Item | Content |
| --- | --- |
| URL | `POST /api/page/check/request` |
| Params | `pageId`: body, string, required, page ID.<br />`targetUser`: body, string, required, target user ID (takeover recipient). |
| Returns | `ApiResult<Map<String,Object>>`: `data` contains `requestId`; fails when a pending request already exists for the page. |

### getCheckRequestList Check request list

| Item | Content |
| --- | --- |
| URL | `GET /api/page/check/request/list` |
| Params | `userId`: query, string, required, target user ID.<br />`status`: query, string, optional, filter by request status. |
| Returns | `ApiResult<List<AppPageCheckReq>>`: check requests addressed to this user. |

### processCheckRequest Process check request

| Item | Content |
| --- | --- |
| URL | `POST /api/page/check/request/process` |
| Params | `requestId`: body, string, required, request ID.<br />`action`: body, string, required, `approve` / `reject`.<br />`comment`: body, string, optional, processing comment. |
| Returns | `ApiResult<Map<String,Object>>`: `data` contains `pageId` and `checkStatus`; with `approve` the page is checked in (the processor is recorded as the check-in user); only pending requests can be processed. |

### cancelCheckRequest Cancel check request

| Item | Content |
| --- | --- |
| URL | `POST /api/page/check/request/cancel` |
| Params | `requestId`: body, string, required, request ID. |
| Returns | `ApiResult<String>`: only the requester can cancel a pending request; the status becomes `rejected` with processing info recorded, `data` is a success message string. |

## PageController — `/page_v1`

Page configuration API V1 (original implementation, kept as the fallback reference for V2): runtime/design-time page definition, language packs, save, and update notifications.

### getPageAndCustom Page definition and custom config

| Item | Content |
| --- | --- |
| URL | `GET /api/page_v1/getPageAndCustom/{idType}/{id}`、`GET /api/page_v1/getPageAndCustom/{idType}/{id}/*` |
| Params | `idType`: path, string, `pageId` or `extendId`.<br />`id`: path, string, the corresponding ID value. |
| Returns | `ApiResult<HashMap<String,Object>>`: runtime page data: `id`, `type`, `appId`, `code`, `releaseContent` (JSON string), `sourceContent` (empty string, no source), `pageLang` (language pack for the current locale), `pageCustom` (the current user's custom config for this page), `pagePerms` (the current user's operation permissions on this page). |

### getPageById Page config by ID

| Item | Content |
| --- | --- |
| URL | `GET /api/page_v1/getPageById/{pageId}` |
| Params | `pageId`: path, string, page ID. |
| Returns | `ApiResult<HashMap<String,Object>>`: full design-time config: same fields as `getPageAndCustom`, but `sourceContent` is the page source while `pageCustom` and `pagePerms` are empty strings. |

### getPageByExtendId Page config by extend ID

| Item | Content |
| --- | --- |
| URL | `GET /api/page_v1/getPageByExtendId/{extendId}` |
| Params | `extendId`: path, string, page tree node extend ID. |
| Returns | Same as `getPageById`, queried by extend ID. |

### getPageLang Page language pack

| Item | Content |
| --- | --- |
| URL | `GET /api/page_v1/getPageLang/{idType}/{id}`、`GET /api/page_v1/getPageLang/{idType}/{id}/*` |
| Params | `idType`: path, string, `pageId` or `extendId`.<br />`id`: path, string, the corresponding ID value. |
| Returns | `ApiResult<HashMap<String,Object>>`: `pageLang` (language pack content for the current locale), `locale` (current locale); used to switch languages after the page has loaded. |

### notifyUpdate Page update notification

| Item | Content |
| --- | --- |
| URL | `GET、POST /api/page_v1/notifyUpdate/{pageId}/{extendId}` |
| Params | `pageId`: path, string, page ID.<br />`extendId`: path, string, page extend ID. |
| Returns | `ApiResult<String>`: publishes the page upgrade event so all clients refresh the page config, `data` is a success message string. |

### savePage Save page config

| Item | Content |
| --- | --- |
| URL | `POST /api/page_v1/savePage` |
| Params | `appPage`: body, `AppPage` entity (no `@RequestBody` annotation; Spring binds it from query/form parameters), common fields such as `id`, `appId`, `code`, `title`, `extendId`, `description`, `sourceContent`; a non-empty `id` means update with the version incremented, an empty `id` means create (version 1). |
| Returns | `ApiResult<AppPage>`: the saved page (version updated); also writes a page log, evicts page caches, and publishes the page update event. |

## PageV2Controller — `/page`

Page configuration API V2 (performance-optimized, paths identical to V1): runtime pageLang/pageCustom/pagePerms queries run in parallel, and `releaseContent` is returned as a native JSON object at runtime.

### getPageAndCustom Page definition and custom config

| Item | Content |
| --- | --- |
| URL | `GET /api/page/getPageAndCustom/{idType}/{id}`、`GET /api/page/getPageAndCustom/{idType}/{id}/*` |
| Params | `idType`: path, string, `pageId` or `extendId`.<br />`id`: path, string, the corresponding ID value. |
| Returns | `ApiResult<HashMap<String,Object>>`: same fields as V1 `getPageAndCustom`, but `releaseContent` is parsed and returned as a native JSON object (falling back to the original string on parse failure); the three secondary queries run in parallel. |

### getPageById Page config by ID

| Item | Content |
| --- | --- |
| URL | `GET /api/page/getPageById/{pageId}` |
| Params | `pageId`: path, string, page ID. |
| Returns | `ApiResult<HashMap<String,Object>>`: full design-time config (serial logic identical to V1): `sourceContent` is the page source, `pageCustom` and `pagePerms` are empty strings, `releaseContent` stays a JSON string. |

### getPageByExtendId Page config by extend ID

| Item | Content |
| --- | --- |
| URL | `GET /api/page/getPageByExtendId/{extendId}` |
| Params | `extendId`: path, string, page tree node extend ID. |
| Returns | Same as `getPageById`, queried by extend ID. |

### getPageLang Page language pack

| Item | Content |
| --- | --- |
| URL | `GET /api/page/getPageLang/{idType}/{id}`、`GET /api/page/getPageLang/{idType}/{id}/*` |
| Params | `idType`: path, string, `pageId` or `extendId`.<br />`id`: path, string, the corresponding ID value. |
| Returns | `ApiResult<HashMap<String,Object>>`: `pageLang` (language pack content for the current locale), `locale` (current locale). |

### notifyUpdate Page update notification

| Item | Content |
| --- | --- |
| URL | `GET、POST /api/page/notifyUpdate/{pageId}/{extendId}` |
| Params | `pageId`: path, string, page ID.<br />`extendId`: path, string, page extend ID. |
| Returns | `ApiResult<String>`: publishes the page upgrade event, `data` is a success message string. |

### savePage Save page config

| Item | Content |
| --- | --- |
| URL | `POST /api/page/savePage` |
| Params | `appPage`: body, `AppPage` entity (no `@RequestBody` annotation; Spring binds it from query/form parameters), common fields such as `id`, `appId`, `code`, `title`, `extendId`, `description`, `sourceContent`; a non-empty `id` means update with the version incremented, an empty `id` means create (version 1). |
| Returns | `ApiResult<AppPage>`: the saved page (version updated); also writes a page log, evicts page caches, and publishes the page update event. |

## ScheduledTaskMonitorController ⚙ — `/system/monitor/scheduled-tasks`

Platform scheduled-task monitoring summary query and manual scan, admin-only.

### list Task monitoring summary

| Item | Content |
| --- | --- |
| URL | `GET /api/system/monitor/scheduled-tasks` |
| Params | None |
| Returns | `ApiResult<ScheduledTaskMonitorSummary>`: scheduled-task monitoring summary (scanned time, task count with active/inactive/running/success/failed/neverRun counts, last error, task snapshot list `tasks`); fails for non-admin users. |

### refresh Immediate task scan

| Item | Content |
| --- | --- |
| URL | `GET、POST /api/system/monitor/scheduled-tasks/refresh` |
| Params | None |
| Returns | `ApiResult<ScheduledTaskMonitorSummary>`: latest summary after an immediate scan; fails for non-admin users. |

## SqlKeyController — `/sk`

Execute SQL by named SQL key, with optional datasource switching.

### exec Execute named SQL

| Item | Content |
| --- | --- |
| URL | `POST /api/sk/{key}` |
| Params | `key`: path, string, required, named SQL key.<br />`connectId`: query, string, optional, datasource connection ID; when provided the SQL runs against that datasource.<br />`paramMap`: body, `Map<String,Object>`, SQL parameters (values for the named SQL template). |
| Returns | `ApiResult<Object>`: determined by the SQL's configured response type: `map`/`object` returns a single-row `Map`, `list`/`array` returns a `List`, otherwise the statement is executed only and `data` is null. |

## StateWorkFlowController — `/stateWorkFlow`

State workflow definition update notification.

### notifyUpdate Workflow update notification

| Item | Content |
| --- | --- |
| URL | `GET、POST /api/stateWorkFlow/notifyUpdate/{procDefId}` |
| Params | `procDefId`: path, string, workflow definition (extend) ID. |
| Returns | `ApiResult<String>`: publishes the state-workflow upgrade event so all clients refresh, `data` is a success message string. |

## SystemInitController — `/system/init`

Platform initialization self-inspection: configuration properties, datasources, and SQL scripts.

### properties Config properties inventory

| Item | Content |
| --- | --- |
| URL | `GET /api/system/init/properties` |
| Params | None |
| Returns | `ApiResult<Map<String,Map<String,String>>>`: config key-values grouped by config file source (auth, market, message, oss, package, sc, seata, weixin_work, workflow, default); keys containing password/secret are masked, unresolved values show "未配置" (not configured). |

### datasource Datasource inventory

| Item | Content |
| --- | --- |
| URL | `GET /api/system/init/datasource` |
| Params | None |
| Returns | `ApiResult<List<Map<String,String>>>`: merged info list of Spring-managed and dynamically registered datasources (`key`, `class`, `jdbcUrl`, `username`, masked `password`, plus `dbType`, `host`, `port`, `dbName` from the config table). |

### validate Datasource connectivity check

| Item | Content |
| --- | --- |
| URL | `GET /api/system/init/datasource/validate` |
| Params | `key`: query, string, optional, datasource key, defaults to `primary`. |
| Returns | `ApiResult<Map<String,String>>`: connectivity check result: `key`, `connected`, `jdbcUrl`, `username`, `product`, `driver`, `durationMs` (milliseconds); fails when the datasource is not found. |

### sql SQL script inventory

| Item | Content |
| --- | --- |
| URL | `GET /api/system/init/sql` |
| Params | None |
| Returns | `ApiResult<Map<String,Object>>`: `db` (SQL scripts stored in the database, each with `sqlKey`, `sql`, `type=db`) and `file` (SQL scripts from files, each with `sqlKey`, `sql`, `type=file`). |

## UploadController — `/upload`

File and configuration upload: attachment upload, config object/JSON persistence, and entity record export as config files.

### uploadFile Upload attachment

| Item | Content |
| --- | --- |
| URL | `POST /api/upload/file` |
| Params | `file`: form-data file field, required, uploaded file.<br />`serviceType`: query/form-data, string, file server type.<br />`tableType`: query/form-data, string, table type (determines the default save path and the attachment source).<br />`root`: query/form-data, string, optional, custom file root directory; the save path is built from it when provided.<br />`isRename`: query/form-data, boolean, optional, rename the file, defaults to true.<br />`objectId`: query/form-data, string, optional, business object ID.<br />`formIds`: query/form-data, string, optional, related form IDs.<br />`genre`: query/form-data, string, optional, file genre.<br />`batchNo`: query/form-data, string, optional, batch number.<br />`invalidTime`: query/form-data, string, optional, invalidation time (datetime format).<br />`validDuration`: query/form-data, integer, optional, validity duration in seconds; overrides `invalidTime` when set.<br />`isThumbnail`: query/form-data, boolean, optional, generate a thumbnail.<br />`onlyThumb`: query/form-data, boolean, optional, generate the thumbnail only.<br />`dimension`: query/form-data, string, optional, thumbnail dimension.<br />`thumbScale`: query/form-data, string, optional, thumbnail scale.<br />`appId`: query/form-data, string, optional, app ID, defaults to the current app.<br />`tenantCode`: query/form-data, string, optional, tenant code, defaults to the current tenant. |
| Returns | `ApiResult<Attachment>`: the uploaded attachment info (`source` set to `tableType`, `storageType` set to `serviceType`). |

### uploadObject Upload config object

| Item | Content |
| --- | --- |
| URL | `POST /api/upload/object` |
| Params | `params`: body, `Map<String,Object>`, required, config content written to the file (Java-serialized object).<br />`fileName`: query/form-data, string, required, file name; the `.config` suffix is appended automatically when missing.<br />`catalog`: query/form-data, string, optional, subdirectory under the config root. |
| Returns | `ApiResult<String>`: `data` is the stored file name; an existing file is first backed up by renaming, and overwritten when the backup rename fails. |

### uploadJson Upload JSON config

| Item | Content |
| --- | --- |
| URL | `POST /api/upload/json` |
| Params | `JsonData`: body, string, required, JSON text (raw request body).<br />`fileName`: query/form-data, string, required, file name; the `.config` suffix is appended automatically when missing.<br />`catalog`: query/form-data, string, optional, subdirectory under the config root. |
| Returns | `ApiResult<String>`: `data` is the stored file name; existing files are handled the same way as `uploadObject`. |

### uploadModel Export entity record

| Item | Content |
| --- | --- |
| URL | `POST /api/upload/model/{entityName}/{id}` |
| Params | `entityName`: path, string, entity (table) name.<br />`id`: path, string, record ID.<br />`fileName`: query/form-data, string, required, export config file name; multiple names can be comma-separated. |
| Returns | `ApiResult<NullResult>`: queries the record's field values (excluding entity default columns and fields listed in `unSaveConfig`) and writes each as a `.config` file; `data` is empty. |
