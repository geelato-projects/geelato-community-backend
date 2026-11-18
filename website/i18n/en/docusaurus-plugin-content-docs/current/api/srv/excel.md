---
title: API · Excel Import/Export
sidebar_label: Excel Import/Export
---
# Excel Import/Export (excel)

3 controllers with 17 endpoints in total. Controllers marked with ⚙ are design-time APIs.

## ExportExcelController — `/export/file`

Export file list query, plus file generation (Excel/Word etc.) either by template or by column metadata, with download and PDF conversion support.

### pageQuery Paged export file query

| Item | Content |
| --- | --- |
| URL | `POST /api/export/file/list` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiPagedResult`: `data` is the paged attachment list (`Attach` entity, records of the `platform_attach` table). |

### exportWps Export by template

| Item | Content |
| --- | --- |
| URL | `POST\|GET /api/export/file/{dataType}/{templateId}` |
| Params | `dataType`: path, string, data parsing type, `mql` or `data`; with `data` the payload is parsed from the gql JSON body, other values fail.<br />`templateId`: path, string, export template ID.<br />`index`: query, string, optional, template index (1-19), defaults to 1.<br />`fileName`: query, string, optional, export file name (defaults to `templateName_timestamp`, template extension appended automatically).<br />`markText`: query, string, optional, watermark text.<br />`markKey`: query, string, optional, watermark style config key (style resolved from system config).<br />`readonly`: query, boolean, whether the exported file is read-only.<br />`isDownload`: query, boolean, when true the file is written to the response as a download.<br />`isPdf`: query, boolean, when true the file is converted to PDF and the original export file is deleted.<br />body (with `dataType=data`): gql JSON containing `valueMapList` (`List<Map>`, multiple rows) and `valueMap` (Map, a single record). |
| Returns | `ApiResult<Attachment>`: `data` is the attachment record of the exported file; with `isDownload=true` the response is written directly to the stream (no JSON body, content-type and file name taken from the attachment); with `isPdf=true` `data` is the PDF attachment record. |

### exportWps Export by column metadata

| Item | Content |
| --- | --- |
| URL | `POST\|GET /api/export/file/column/meta/list` |
| Params | `appId`: query, string, optional, app ID, defaults to the `appId` header.<br />`fileName`: query, string, optional, export file name (defaults to `exportExcelByColumnMeta_timestamp.xlsx`).<br />`markText`: query, string, optional, watermark text.<br />`markKey`: query, string, optional, watermark style config key.<br />`readonly`: query, boolean, whether the exported file is read-only.<br />`isDownload`: query, boolean, when true the file is written to the response as a download.<br />`isPdf`: query, boolean, when true the file is converted to PDF and the original export file is deleted.<br />body: gql JSON containing `valueMapList` (`List<Map>`, multiple rows), `valueMap` (Map, a single record), `column` (`List<ExportColumn>`, table header column definition, required) and `meta` (`List<PlaceholderMeta>`, placeholder data definition, required). |
| Returns | `ApiResult<Attachment>`: `data` is the attachment record of the exported file; with `isDownload=true` the response is written directly to the stream (no JSON body, content-type and file name taken from the attachment); with `isPdf=true` `data` is the PDF attachment record. |

### toPdf Convert export file to PDF

| Item | Content |
| --- | --- |
| URL | `GET /api/export/file/toPdf/{id}` |
| Params | `id`: path, string, attachment ID of the Excel export file. |
| Returns | `ApiResult<String>`: `data` is the attachment ID of the converted PDF; fails when the file or its attachment record is missing or conversion fails. |

## ExportTemplateController — `/export/template`

Export/import template (`ExportTemplate`) management: paged and list queries, copy, save, delete, template file generation and template content lookup.

### pageQuery Paged template query

| Item | Content |
| --- | --- |
| URL | `GET /api/export/template/pageQuery` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiPagedResult`: `data` is the paged template list (`ExportTemplate`). |

### query List templates

| Item | Content |
| --- | --- |
| URL | `GET /api/export/template/query` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiResult<List<ExportTemplate>>`: `data` is the template list (ordered by the sort parameter). |

### get Get template detail

| Item | Content |
| --- | --- |
| URL | `GET /api/export/template/get/{id}` |
| Params | `id`: path, string, template ID. |
| Returns | `ApiResult<ExportTemplate>`: `data` is the template detail. |

### copy Copy template

| Item | Content |
| --- | --- |
| URL | `GET /api/export/template/copy/{id}` |
| Params | `id`: path, string, ID of the template to copy. |
| Returns | `ApiResult<ExportTemplate>`: `data` is the new template record (title suffixed with ` - 副本` (copy), new ID generated). |

### createOrUpdate Save template

| Item | Content |
| --- | --- |
| URL | `POST /api/export/template/createOrUpdate` |
| Params | `form`: body, `ExportTemplate` entity; empty `id` creates, otherwise updates; a `template` file is generated automatically after saving. |
| Returns | `ApiResult<ExportTemplate>`: `data` is the saved template record. |

### isDelete Delete template

| Item | Content |
| --- | --- |
| URL | `DELETE /api/export/template/isDelete/{id}` |
| Params | `id`: path, string, template ID; fails when the template does not exist. |
| Returns | `ApiResult<NullResult>`: sets `enableStatus` to disabled and deletes the record; `data` is empty. |

### generateFile Generate template file

| Item | Content |
| --- | --- |
| URL | `POST /api/export/template/generateFile/{id}` |
| Params | `id`: path, string, template ID.<br />`fileType`: body, string, file type to generate, `template` (import template file) or `meta` (metadata/rule file), multiple values comma-separated. |
| Returns | `ApiResult<Attachment>`: `data` is the generated attachment record; fails when params are missing or the template does not exist. |

### indexTemplate Template index map

| Item | Content |
| --- | --- |
| URL | `GET /api/export/template/index/{id}` |
| Params | `id`: path, string, template ID. |
| Returns | `ApiResult<Map<Integer,Object>>`: `data` maps template index (1-9) to template file name, non-empty templates only; returns an empty map when the template does not exist. |

### indexTemplate Template content by index

| Item | Content |
| --- | --- |
| URL | `GET /api/export/template/base64/{id}/{index}` |
| Params | `id`: path, string, template ID.<br />`index`: path, string, template index (1-19). |
| Returns | `ApiResult<String>`: `data` is the template content string for that index (Base64Helper JSON with the file name and base64 file content); fails when the template does not exist, throws when the index is out of range. |

## ImportExcelController — `/import`

Excel data import: import template lookup, import from attachment/uploaded file, and complex template parsing.

### getTemplate Get import template

| Item | Content |
| --- | --- |
| URL | `GET /api/import/template/{templateId}` |
| Params | `templateId`: path, string, import template ID. |
| Returns | `ApiResult<ExportTemplate>`: `data` is the import template record. |

### importAttach Import from attachment

| Item | Content |
| --- | --- |
| URL | `POST\|GET /api/import/attach/{importType}/{templateId}/{attachId}` |
| Params | `importType`: path, string, import type: `part` partial import / `all` full import (interrupt and roll back on error).<br />`templateId`: path, string, import template ID.<br />`attachId`: path, string, attachment ID of the business data Excel file.<br />`index`: query, string, optional, template index. |
| Returns | `ApiResult`: on success `data` is the import transaction UUID (string) and `msg` the import count summary; on data validation failure `data` is the error report file attachment (`Attachment`), see the error file for details. |

### importFile Import from upload

| Item | Content |
| --- | --- |
| URL | `POST\|GET /api/import/file/{importType}/{templateId}` |
| Params | `importType`: path, string, import type: `part` partial import / `all` full import (interrupt and roll back on error).<br />`templateId`: path, string, import template ID.<br />`index`: query, string, optional, template index.<br />body: form-data file field `file`, the uploaded business data Excel (.xls/.xlsx). |
| Returns | `ApiResult`: on success `data` is the import transaction UUID (string) and `msg` the import count summary; on data validation failure `data` is the error report file attachment (`Attachment`), see the error file for details. |

### importComplexExcel Parse complex Excel

| Item | Content |
| --- | --- |
| URL | `POST\|GET /api/import/complex/{importType}/{templateId}/{attachId}` |
| Params | `importType`: path, string, import type (reserved parameter, kept for signature compatibility).<br />`templateId`: path, string, import template ID.<br />`attachId`: path, string, attachment ID of the business data Excel file. |
| Returns | `ApiResult<Map<String,Object>>`: `data` is the flattened parsed JSON (not persisted): fixed cell fields and list regions are flattened on the same level, list keys are the list `fieldName` with row arrays as values. |
