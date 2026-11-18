---
title: API · App Packaging
sidebar_label: App Packaging
---
# App Packaging (pack)

2 controllers with 12 endpoints in total. Controllers marked with ⚙ are design-time APIs.

## AppVersionController — `/app/version`

App version management: paged version queries, detail, create or update, deletion, version uniqueness validation and reading version package data.

### pageQuery Page query

| Item | Content |
| --- | --- |
| URL | `POST /api/app/version/pageQuery` |
| Params | Pagination and filter params: see [Conventions](../reference.md). |
| Returns | `ApiPagedResult`: `data` is the paged app version data (paging info on the outer envelope). |

### isDelete Delete version

| Item | Content |
| --- | --- |
| URL | `DELETE /api/app/version/isDelete/{id}` |
| Params | `id`: path, string, version ID. |
| Returns | `ApiResult<NullResult>`: marks the version deleted; `data` is empty; fails when the version does not exist. |

### get Get version detail

| Item | Content |
| --- | --- |
| URL | `GET /api/app/version/get/{id}` |
| Params | `id`: path, string, version ID. |
| Returns | `ApiResult<AppVersion>`: `data` is the app version record. |

### getPackage Read package data of a version

| Item | Content |
| --- | --- |
| URL | `GET /api/app/version/package/{id}` |
| Params | `id`: path, string, version ID. |
| Returns | `ApiResult<AppPackData>`: `data` is the app package data of the version (the `.gdp` content is read from the `.zgdp` archive or package file and parsed into `AppPackData`); throws `PackException` (60xxx error codes) when the version does not exist, the package path or file is missing, or reading fails. |

### createOrUpdate Create or update version

| Item | Content |
| --- | --- |
| URL | `POST /api/app/version/createOrUpdate` |
| Params | body: `AppVersion` structure; when `id` is empty a version is created, otherwise updated. |
| Returns | `ApiResult<AppVersion>`: `data` is the created/updated version record. |

### validate Validate version uniqueness

| Item | Content |
| --- | --- |
| URL | `POST /api/app/version/validate` |
| Params | body: `AppVersion` structure, with `id`, `version`, `appId`, `tenantCode` used for the check. |
| Returns | `ApiResult<Boolean>`: `data` indicates whether `version` is unique among non-deleted versions of the same app and tenant (excluding itself by `id`). |

## PackageController — `/package`

This controller is a literal `@Controller` (class-level `@RequestMapping("/package")`) and does not get the `/api` automatic prefix of the `@ApiRestController` family, so the URLs below carry no `/api` prefix.

App packaging and deployment: package an app, merge packages, version package upload/download, deploy a version package and rollback.

### packetApp Package an app

| Item | Content |
| --- | --- |
| URL | `GET\|POST /package/packet/{appId}` |
| Params | `appId`: path, string, required, app ID.<br />`version`: query, string, optional, package version, defaults to an auto-generated `app code + timestamp`.<br />`description`: query, string, optional, version description, defaults to "app package generated from the current environment".<br />`appointMetas`: body, optional, `Map<String,String>`, keys are table names and values the packaging scope of each table (parsed by `PackageUtils.pickMetaData`); when omitted all table data is packaged. |
| Returns | `ApiResult<AppVersion>`: `data` is the app version record created by this packaging (draft status; the package is written as `.gdp` and compressed into a `.zgdp` registered as an attachment); with the v2 engine it goes through `packetV2`; business errors such as "no packagable app found" throw `PackException`. |

### packetMergeApp Merge packages

| Item | Content |
| --- | --- |
| URL | `GET\|POST /package/packet/merge` |
| Params | `appId`: query, string, target app ID.<br />`version`: query, string, optional, package version, auto-generated when omitted.<br />`description`: query, string, optional, version description, defaults to "package produced by comparison merge".<br />`appointMetas`: body, optional, `Map<String,Map<String,String>>`, keys are the version numbers (`AppVersion.version`) of the packages to merge and values the table name → packaging scope mappings. |
| Returns | `ApiResult<AppVersion>`: `data` is the app version record created by the merge packaging; with the v2 engine it goes through `packetMergeV2`. |

### downloadPackage Download version package

| Item | Content |
| --- | --- |
| URL | `GET /package/downloadPackage/{versionId}` |
| Params | `versionId`: path, string, version ID. |
| Returns | Writes directly to the response stream (no JSON body): `application/octet-stream` with `Content-Disposition: attachment`, streaming the version package file. |

### uploadPackage Upload version package

| Item | Content |
| --- | --- |
| URL | `POST /package/uploadPackage/{appId}` |
| Params | `file`: form-data, file, required, the uploaded package file (saved to the configured upload directory).<br />`appId`: path, string, target app ID. |
| Returns | `ApiResult<AppVersion>`: `data` is the app version record created by the registration (storing the upload path and packaging time). |

### deployPackage Deploy version package

| Item | Content |
| --- | --- |
| URL | `GET /package/deploy/{versionId}` |
| Params | `versionId`: path, string, version ID. |
| Returns | `ApiResult`: `data` is empty and `msg` reads "App deployed successfully"; the v1 engine clears and rewrites the app data in a transaction and then refreshes the app metadata cache, the v2 engine goes through `deployV2` (backing up the current version first, deploying in a declarative transaction); deployment is forbidden in environments with `env=init_source`; failed package validation, missing metadata, deployment failures, etc. throw `PackException` (60xxx error codes). |

### rollback Rollback to backup version

| Item | Content |
| --- | --- |
| URL | `GET /package/rollback/{appId}` |
| Params | `appId`: path, string, app ID. |
| Returns | `ApiResult<?>`: rolls back by deploying the latest backup version (calls `rollbackV2`, v2 engine only) and returns the deployment result; throws `PackException` when no backup version exists. |
