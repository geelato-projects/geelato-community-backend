---
title: API · Static Sites
sidebar_label: Static Sites
---
# Static Sites (site)

1 controller with 13 endpoints in total. All endpoints in this domain are design-time APIs.

## StaticSiteController ⚙ — `/site/statics`

Static site management and site folder file operations: create/update/delete sites and queries, code uniqueness validation, folder tree nodes, file listing, file and folder create/delete/rename, upload and download.

### pageQuery Page query

| Item | Content |
| --- | --- |
| URL | `POST /api/site/statics/pageQuery` |
| Params | Pagination and filter params: see [Conventions](../reference.md). |
| Returns | `ApiPagedResult`: `data` is the paged static site data (paging info on the outer envelope). |

### query List query

| Item | Content |
| --- | --- |
| URL | `GET /api/site/statics/query` |
| Params | Pagination and filter params: see [Conventions](../reference.md). |
| Returns | `ApiResult<List<StaticSite>>`: `data` is the list of matching static sites. |

### get Get site detail

| Item | Content |
| --- | --- |
| URL | `GET /api/site/statics/get/{id}` |
| Params | `id`: path, string, site ID. |
| Returns | `ApiResult<StaticSite>`: `data` is the static site record. |

### createOrUpdate Create or update site

| Item | Content |
| --- | --- |
| URL | `POST /api/site/statics/createOrUpdate` |
| Params | body: `StaticSite` structure; when `id` is empty a site is created, otherwise updated, and the site folder is created under the configured site root directory accordingly. |
| Returns | `ApiResult<StaticSite>`: `data` is the created/updated site record. |

### isDelete Delete site

| Item | Content |
| --- | --- |
| URL | `DELETE /api/site/statics/isDelete/{id}` |
| Params | `id`: path, string, site ID. |
| Returns | `ApiResult<NullResult>`: deletes the site folder and marks the record deleted; `data` is empty; fails when the site does not exist. |

### validate Validate code uniqueness

| Item | Content |
| --- | --- |
| URL | `POST /api/site/statics/validate` |
| Params | body: `StaticSite` structure, with `id`, `code`, `appId`, `tenantCode` used for the check. |
| Returns | `ApiResult<Boolean>`: `data` indicates whether `code` is unique among non-deleted sites of the same app and tenant (excluding itself by `id`). |

### treeNode Folder tree nodes

| Item | Content |
| --- | --- |
| URL | `POST /api/site/statics/treeNode` |
| Params | `path`: body, string, optional, folder path; when empty, root nodes are built from the sites.<br />`appId`: body, string, optional, app ID, defaults to the request header.<br />`tenantCode`: body, string, optional, tenant code, defaults to the request header. |
| Returns | `ApiResult<Set<TreeNodeData>>`: when `path` is empty, `data` is the list of site root nodes for that app and tenant (an error is raised when both `appId` and `tenantCode` are empty); otherwise `data` is the list of folder tree nodes under the `path` directory. |

### queryFile List files in folder

| Item | Content |
| --- | --- |
| URL | `POST /api/site/statics/queryFile` |
| Params | `path`: body, string, required, folder path.<br />`type`: body, string, optional, file type filter, defaults to `all`. |
| Returns | `ApiResult<Set<FileInfo>>`: `data` is the list of files under the `path` directory (sorted); fails when `path` is empty. |

### batchDelete Batch delete files or folders

| Item | Content |
| --- | --- |
| URL | `POST /api/site/statics/batchDelete` |
| Params | `path`: body, string, required, paths to delete, comma-separated (duplicates removed automatically). |
| Returns | `ApiResult<NullResult>`: deletes the files or folders one by one; `data` is empty; fails when `path` is empty. |

### download Download file

| Item | Content |
| --- | --- |
| URL | `POST /api/site/statics/download` |
| Params | `path`: body, string, required, file path (a directory path raises an error).<br />`isPreview`: body, boolean, optional, preview mode, defaults to false. |
| Returns | Writes directly to the response stream (no JSON body): outputs the file download/preview stream (cache headers are set; content type and inlining are handled by the download service based on the file type). |

### upload Upload file

| Item | Content |
| --- | --- |
| URL | `POST /api/site/statics/upload` |
| Params | `file`: form-data, file, required, the uploaded file.<br />`path`: query, string, required, target root directory (fails when it does not exist).<br />`isCompress`: query, boolean, optional, whether to extract archives (zip/tar/rar/7z, etc.; saved as a regular file when false or not an archive).<br />`isByStep`: query, boolean, optional, whether to extract step by step.<br />`isExist`: query, int, optional, conflict handling for existing files: 1 overwrite, 2 auto-rename, any other value raises an error. |
| Returns | `ApiResult<NullResult>`: `data` is empty. |

### updateName Rename file or folder

| Item | Content |
| --- | --- |
| URL | `POST /api/site/statics/updateName` |
| Params | `path`: body, string, required, current path.<br />`name`: body, string, required, new name (must not contain special characters). |
| Returns | `ApiResult<NullResult>`: `data` is empty; fails when params are empty or the name contains special characters. |

### createFolder Create folder

| Item | Content |
| --- | --- |
| URL | `POST /api/site/statics/createFolder` |
| Params | `path`: body, string, required, parent folder path.<br />`name`: body, string, required, folder name (must not contain special characters). |
| Returns | `ApiResult<NullResult>`: `data` is empty; fails when params are empty or the name contains special characters. |
