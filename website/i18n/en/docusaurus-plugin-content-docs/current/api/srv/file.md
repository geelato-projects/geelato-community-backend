---
title: API · Files & Attachments
sidebar_label: Files & Attachments
---
# Files & Attachments (file)

3 controllers with 21 endpoints in total. Controllers marked with ⚙ are design-time APIs.

## AttachController — `/attach`

Attachment record management: copy, quote, query, update, replace, existence validation, storage migration, deletion and Excel-to-PDF conversion.

### copy Copy attachment

| Item | Content |
| --- | --- |
| URL | `POST /api/attach/copy/{id}` |
| Params | `id`: path, string, ID of the attachment to copy.<br />`genre`: body, string, optional, business genre tag (written to the new attachment's `genre`).<br />`name`: body, string, optional, new file name (original extension appended automatically).<br />`invalidTime`: body, string, optional, invalidation time (DATETIME format). |
| Returns | `ApiResult<Attachment>`: `data` is the new attachment record created by the copy; fails when the attachment or its file is missing. |

### quote Quote attachment

| Item | Content |
| --- | --- |
| URL | `POST /api/attach/quote/{id}` |
| Params | `id`: path, string, ID of the attachment to quote.<br />`genre`: body, string, optional, business genre tag.<br />`name`: body, string, optional, new file name (original extension appended automatically).<br />`invalidTime`: body, string, optional, invalidation time (DATETIME format). |
| Returns | `ApiResult<Attachment>`: `data` is the new quote attachment record (file path shared with the source attachment, `pid` pointing to it); fails when the source attachment is missing. |

### get Get attachment detail

| Item | Content |
| --- | --- |
| URL | `GET /api/attach/get/{id}` |
| Params | `id`: path, string, attachment ID. |
| Returns | `ApiResult<Attachment>`: `data` is the attachment record. |

### image List child attachments

| Item | Content |
| --- | --- |
| URL | `POST /api/attach/image/{id}` |
| Params | `id`: path, string, parent attachment ID (`pid`). |
| Returns | `ApiResult<List<Attachment>>`: `data` is the list of attachments under that `pid` (e.g. thumbnails of an image). |

### update Update attachment

| Item | Content |
| --- | --- |
| URL | `POST /api/attach/update/{id}` |
| Params | `id`: path, string, attachment ID.<br />body: `Map<String,Object>`, attachment field name to value mapping (populated onto `Attachment` fields). |
| Returns | `ApiResult<Attachment>`: `data` is the updated attachment record. |

### replace Replace attachment

| Item | Content |
| --- | --- |
| URL | `POST /api/attach/replace/{sourceId}/{targetId}` |
| Params | `sourceId`: path, string, source attachment ID.<br />`targetId`: path, string, target attachment ID.<br />body: `Map<String,Object>`, field updates for the target attachment. |
| Returns | `ApiResult<Attachment>`: `data` is the target attachment record (fields updated, source `pid` inherited and tagged `Replace`, its ID replaced with the source attachment ID; the source record is kept under a new ID); fails when the source or target attachment is missing. |

### validate Validate file existence

| Item | Content |
| --- | --- |
| URL | `POST /api/attach/valid` |
| Params | `attachmentIds`: body, string, comma-separated attachment IDs. |
| Returns | `ApiResult<Map<String,Boolean>>`: `data` maps attachment ID to whether its file exists (boolean); fails when the IDs are empty or the attachments do not exist. |

### updateStorageType Migrate storage type

| Item | Content |
| --- | --- |
| URL | `POST /api/attach/storage/{type}` |
| Params | `type`: path, string, target storage type: `local` local storage / `aliyun` Alibaba Cloud OSS.<br />`attachmentIds`: body, string, comma-separated attachment IDs. |
| Returns | `ApiResult<Map<String,String>>`: `data` maps attachment ID to a processing message, containing only problem items (file missing, migration failed, storage type unchanged); fails when the IDs are empty or the attachments do not exist. |

### list List attachments

| Item | Content |
| --- | --- |
| URL | `POST /api/attach/list` |
| Params | body: `Map<String,Object>`, query conditions and paging params (e.g. `ids`, `pids`, `batchNo`, `storageType`; paging keys `current`/`pageSize`/`order`, see `FileHandler.getAttachments`). |
| Returns | `ApiResult<List<Attachment>>`: `data` is the matching attachment list. |

### pageQuery Page query

| Item | Content |
| --- | --- |
| URL | `POST /api/attach/pageQuery` |
| Params | `current`: body, long, page number, defaults to 1.<br />`pageSize`: body, int, page size, defaults to 10.<br />`order`: body, string, sort (`field\|direction`, `\|` replaced by a space, defaults to `create_at desc`).<br />remaining keys: body, used as query conditions. |
| Returns | `ApiPagedResult`: `data` is `DataItems<Attachment>` (`items` attachment array + `total` count), with paging info on the outer envelope. |

### columnType Column value options

| Item | Content |
| --- | --- |
| URL | `GET /api/attach/column/{type}` |
| Params | `type`: path, string, column type: `source`, `storageType` (enum values returned directly), `type`, `resolution` (distinct values queried from the table). |
| Returns | `ApiResult<List<String>>`: `data` is the list of available values for that column. |

### remove Delete attachment

| Item | Content |
| --- | --- |
| URL | `DELETE /api/attach/remove/{id}` |
| Params | `id`: path, string, attachment ID.<br />`isRemoved`: query, boolean, whether to physically delete from the database (true deletes, false marks as deleted).<br />`deleteOss`: query, boolean, optional, defaults to false, whether to also delete the OSS object. |
| Returns | `ApiResult<NullResult>`: `data` is empty. |

### batchRemove Batch delete attachments

| Item | Content |
| --- | --- |
| URL | `POST /api/attach/remove/{type}` |
| Params | `type`: path, string, deletion scope; with `batchNo` the batch numbers are resolved from the attachment IDs and every attachment of the same batches is deleted.<br />`isRemoved`: query, boolean, whether to physically delete from the database.<br />`attachmentIds`: body, string, comma-separated attachment IDs. |
| Returns | `ApiResult<NullResult>`: deletes the attachments one by one; `data` is empty; fails when the IDs are empty. |

### toPdf Convert attachment to PDF

| Item | Content |
| --- | --- |
| URL | `GET /api/attach/toPdf/{id}` |
| Params | `id`: path, string, attachment ID; only .xlsx/.xls files are supported. |
| Returns | `ApiResult<String>`: `data` is the attachment ID of the converted PDF; fails when the attachment or file is missing, the format is unsupported, or conversion fails. |

## CompressController — `/pack`

Attachment packaging (zip, split into packages) and compressed-package attachment ID resolution, supporting compression by attachment ID, batch number and thumbnail.

### byFileIds Compress by file IDs

| Item | Content |
| --- | --- |
| URL | `POST /api/pack/by/fileIds` |
| Params | body: `CompressRequestBody` structure: `attachmentIds` (string, comma-separated attachment IDs), `batchNos` (string, comma-separated batch numbers), `fileName` (string, archive file name), `serviceType` (string, storage service type), `genre` (string, business genre tag), `validDuration` (int, validity duration in seconds, invalidation time derived automatically), `invalidTime` (date, invalidation time), `amount` (int, max files per archive, system default when omitted), `gql` (string, gql statement). |
| Returns | `ApiResult<Map<String,Object>>`: `data` contains `attachmentIds` (list of archive attachment IDs) and `batchNo` (batch number); fails when compression fails. |

### byBatchNos Compress by batch numbers

| Item | Content |
| --- | --- |
| URL | `POST /api/pack/by/batch` |
| Params | body: same `CompressRequestBody` structure as `by/fileIds`. |
| Returns | `ApiResult<Map<String,Object>>`: `data` contains `attachmentIds` (list of archive attachment IDs) and `batchNo` (batch number); fails when compression fails. |

### downloadFiles Pack or resolve attachments

| Item | Content |
| --- | --- |
| URL | `POST /api/pack/by/{type}/{column}` |
| Params | `type`: path, string, `pack` to compress / `simple` to resolve attachment IDs only.<br />`column`: path, string, `id` by attachment ID / `batchNo` resolves batch numbers from the IDs and expands to all attachments of the same batches.<br />`attachmentIds`: body, string, comma-separated attachment IDs. |
| Returns | `ApiResult<List<String>>`: `data` is the attachment ID list (for `pack`, the archive attachment IDs, file name taken from the current timestamp); throws when attachments are missing or compression fails. |

### byThumbs Compress thumbnails

| Item | Content |
| --- | --- |
| URL | `POST /api/pack/by/thumb` |
| Params | body: same `CompressRequestBody` structure as `by/fileIds`; `attachmentIds` is resolved by `pid` into their child attachment (thumbnail) IDs before compressing. |
| Returns | `ApiResult<Map<String,Object>>`: `data` contains `attachmentIds` (list of archive attachment IDs) and `batchNo` (batch number); fails when compression fails. |

## OssController — `/oss`

OSS (Alibaba Cloud object storage) management: bucket statistics and dual-side (database vs cloud) file verification as an async task. Registered only when the OSS component is enabled.

### stats Bucket statistics

| Item | Content |
| --- | --- |
| URL | `GET /api/oss/stats` |
| Params | None |
| Returns | `ApiResult<Map<String,Object>>`: `data` is the OSS bucket statistics (storage usage, file count, etc.); fails when the statistics cannot be fetched. |

### startVerify Start verify task

| Item | Content |
| --- | --- |
| URL | `POST /api/oss/start-verify` |
| Params | None |
| Returns | `ApiResult<Map<String,Object>>`: `data` contains `taskId`; verification runs asynchronously, task results are kept for 30 minutes, poll `verify-status` for progress. |

### verifyStatus Verify task progress

| Item | Content |
| --- | --- |
| URL | `GET /api/oss/verify-status/{taskId}` |
| Params | `taskId`: path, string, verify task ID. |
| Returns | `ApiResult<Map<String,Object>>`: `data` contains `status` (`running`/`completed`/`failed`), `total`, `checked`, `existCount`, `missingCount`, `results` (each item with `attachId`, `name`, `objectName`, `type`, `exists`, plus `error` when the check throws), and `errorMessage` on failure; fails when the task does not exist or has expired. |
