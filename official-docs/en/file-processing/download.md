# File Download

This page explains the current Geelato file download and preview capability, including runtime entry points and the difference between local files and OSS files.

The description is based on:

- `DownloadController`
- `FileHandler`
- `BaseHandler`
- `AttachController`

## Download Entry

The main runtime entry is:

- `GET /api/resources/file`

Common parameters include:

- `id`
- `isPdf`
- `isPreview`
- `isThumbnail`

## How the Runtime Resolves the File

The runtime does not directly expose a disk path.

The flow is:

1. load attachment metadata by attachment ID
2. optionally switch to a thumbnail record
3. decide whether the attachment is local or OSS based
4. open a stream or local file
5. write the response in preview or download mode

## Local File Download

When:

- `objectId` is empty

the runtime treats the attachment as a local file and reads:

- `Attachment.path`

as a local filesystem path.

## OSS File Download

When:

- `objectId` is not empty

the runtime treats the attachment as an OSS object.

It then:

1. uses `Attachment.path` as the object name
2. calls `FileHelper.getFile(...)`
3. reads the OSS input stream
4. sends it through the same unified download output

## Preview and PDF Conversion

The download entry supports:

- `isPreview`
- `isPdf`

If `isPdf=true`, the runtime converts the original file to PDF first, then outputs the PDF file.

The temporary converted PDF file is stored under:

- `geelato.upload.convert-directory`

There is also:

- `GET /api/attach/toPdf/{id}`

which converts an Excel attachment into a new PDF attachment record.

## Config File Download

The runtime also provides:

- `GET /api/resources/json`

to read `.config` files from:

- `geelato.upload.config-directory`

## Related Attachment APIs

Related attachment APIs include:

- `GET /api/attach/get/{id}`
- `POST /api/attach/image/{id}`
- `POST /api/attach/valid`
- `POST /api/attach/storage/{type}`
- `DELETE /api/attach/remove/{id}`

These support validation, storage migration, and deletion workflows around download.

## Suggested Reading

- [File Upload](upload.md)
- [OSS Module](../system-config/oss.md)
