# File Upload

This page explains the current Geelato file upload capability, including upload entry points, storage modes, attachment metadata tables, and OSS configuration.

The description is based on:

- `UploadController`
- `FileHandler`
- `UploadService`
- `Attachment`

## Overall Flow

The main upload entry is:

- `POST /api/upload/file`

The runtime flow is:

1. `UploadController` receives the upload request
2. it calculates the save path from `tableType / tenantCode / appId / root / isRename`
3. it builds `FileParam`
4. `FileHandler` chooses local storage or Aliyun OSS by `serviceType`
5. the binary content is stored
6. attachment metadata is persisted through `AccessoryHandler`
7. an `Attachment` is returned

So the framework clearly separates:

- binary storage
- attachment metadata persistence

## Upload Entry

### Standard File Upload

API:

- `POST /api/upload/file`

Important parameters include:

- `file`
- `serviceType`
- `tableType`
- `root`
- `isRename`
- `objectId`
- `formIds`
- `genre`
- `batchNo`
- `invalidTime`
- `validDuration`
- `isThumbnail`
- `onlyThumb`
- `dimension`
- `thumbScale`
- `appId`
- `tenantCode`

The most important ones are:

- `serviceType`
  - chooses local or OSS storage
- `tableType`
  - marks the attachment source
- `root`
  - overrides the local root directory
- `isRename`
  - decides whether the original filename is replaced with a UID

### Object and JSON Upload

The runtime also provides:

- `POST /api/upload/object`
- `POST /api/upload/json`
- `POST /api/upload/model/{entityName}/{id}`

These are not normal attachment uploads. They write serialized content into:

- `geelato.upload.config-directory`

## File Storage Location

### Local Root Directories

Local storage is controlled by:

- `geelato.upload.root-directory`
- `geelato.upload.convert-directory`
- `geelato.upload.config-directory`

They are initialized in `UploadService` as static roots.

### How the Path Is Built

For standard uploads without a custom `root`, the runtime usually uses:

- `UploadService.getRootSavePath(...)`

The path usually contains:

- the upload root
- the attachment source such as `attach`
- `tenantCode/appId`
- a date-based directory
- the final filename

### Custom Root

If `root` is passed in the request, the runtime switches to:

- `UploadService.getSaveRootPath(root, fileName, isRename)`

So the file goes directly under the requested directory instead of the default upload root layout.

## Local and OSS Storage

`FileHandler` is the actual storage dispatcher.

The rule is simple:

- `serviceType = aliyun` means OSS
- otherwise it falls back to local storage

### Local Storage

In local mode:

- the file is written to disk
- `Attachment.path` stores the local absolute path
- `Attachment.objectId` is normally empty

### OSS Storage

In OSS mode:

1. `FileHelper.putFile(...)` uploads to Aliyun OSS
2. OSS returns `objectId` and `objectName`
3. attachment metadata is then saved
4. `Attachment.path` stores the OSS object name
5. `Attachment.objectId` stores the OSS object ID

So `path` is not always a local path. When `objectId` is present, the file is usually stored in OSS.

## Thumbnail Support

Image uploads can enable:

- `isThumbnail`

The current behavior includes:

- only image files generate thumbnails
- multiple thumbnail resolutions are supported
- `onlyThumb=true` can keep only the thumbnail records
- parent-child relations are linked through `pid`

## Attachment Metadata

The framework stores file metadata through:

- `Attachment`

Important fields include:

- `id`
- `pid`
- `appId`
- `name`
- `type`
- `genre`
- `size`
- `path`
- `objectId`
- `formIds`
- `invalidTime`
- `batchNo`
- `resolution`

## Actual Metadata Tables

Runtime attachment queries aggregate three tables:

- `platform_attach`
- `platform_compress`
- `platform_resources`

They are unified as one `Attachment` view for the upper layer.

The SQL also derives:

- `storageType = aliyun`
- `storageType = local`

from the presence of `object_id`.

## OSS Configuration

Aliyun OSS configuration comes from:

- `properties/oss.properties`

Key properties are:

- `geelato.oss.accessKeyId`
- `geelato.oss.accessKeySecret`
- `geelato.oss.endPoint`
- `geelato.oss.bucketName`
- `geelato.oss.region`

In quickstart they are normally injected through environment variables.

If OSS is not configured and `serviceType` is set to `aliyun`, `FileHandler` throws a clear error.

## Related Attachment APIs

After upload, attachment metadata can be managed through `AttachController`, including:

- `POST /api/attach/copy/{id}`
- `POST /api/attach/quote/{id}`
- `GET /api/attach/get/{id}`
- `POST /api/attach/update/{id}`
- `POST /api/attach/list`
- `POST /api/attach/pageQuery`
- `DELETE /api/attach/remove/{id}`
- `POST /api/attach/storage/{type}`
- `GET /api/resources/file?id={id}&isPreview=true`

Notes:

- `resources/file` returns the file content by attachment `id`
- `isPreview=true` typically returns preview-friendly content type for images/PDFs

## Suggested Reading

- [File Download](download.md)
- [OSS Module](../system-config/oss.md)
