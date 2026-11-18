---
title: API · OCR
sidebar_label: OCR
---
# OCR (ocr)

6 controllers, 24 endpoints in total. Endpoints marked with ⚙ are design-time APIs.

## OCRController — `/ocr`

Template supporting APIs for PDF recognition: parse PDF annotation meta, resolve or clear PDF content by template, rule testing, and time format validation.

### meta Parse annotation meta

| Item | Content |
| --- | --- |
| URL | `GET /api/ocr/pdf/meta/{fileId}` |
| Params | `fileId`: path, string, attachment ID of the PDF file. |
| Returns | `ApiResult<List<PDFAnnotationMeta>>`: `data` is the annotation meta list resolved from the PDF (via the OCR plugin). |

### meta Simple template resolve

| Item | Content |
| --- | --- |
| URL | `GET /api/ocr/pdf/resolve0` |
| Params | `fileId`: query, string, attachment ID of the PDF file.<br />`templateId`: query, string, OCR PDF template ID.<br />`wholeContent`: query, boolean, optional, default false, whether to return the whole resolved PDF content. |
| Returns | With `wholeContent=true`, `data` is a `PDFResolveData` (whole PDF content resolved by the template meta); otherwise a `List<PDFAnnotationPickContent>` (contents picked at annotation positions). Fails when the file does not exist or the template/template meta is empty. |

### meta Clear PDF region content

| Item | Content |
| --- | --- |
| URL | `POST /api/ocr/pdf/content/clear` |
| Params | `fileId`: query, string, attachment ID of the PDF file.<br />`annotationPositionMetaList`: body, `List<AnnotationPositionMeta>`, annotation position regions whose content should be cleared. |
| Returns | `ApiResult<String>`: `data` is the new attachment ID of the saved PDF with cleared content (genre `clearPdf`). |

### metaResolve Resolve PDF by template

| Item | Content |
| --- | --- |
| URL | `GET /api/ocr/pdf/resolve` |
| Params | `fileId`: query, string, attachment ID of the PDF file.<br />`templateId`: query, string, OCR PDF template ID; supports multiple comma-separated IDs, tried one by one.<br />`wholeContent`: query, boolean, optional, default false, whether to return the whole resolved PDF content. |
| Returns | On a template match, with `wholeContent=true` returns `ApiResult<OcrPdfWhole>` (with formatted contents), otherwise `ApiResult<List<OcrPdfContent>>` (formatted field contents); fails with `Template matching failure` when no template matches. Candidate templates must first pass the whole-content regexp validation. |

### ruleTest Test template rules

| Item | Content |
| --- | --- |
| URL | `POST /api/ocr/pdf/meta/test` |
| Params | `data`: body, `Map<String,Object>`, keys: `content` (content string to process), `rule` (rule JSON array string, parsed into `List<OcrPdfMetaRule>`), `metas` (meta JSON array string, parsed into `List<OcrPdfContent>`). |
| Returns | `ApiResult<String>`: `data` is the content string processed by the rules. |

### analysis Analyze template PDF

| Item | Content |
| --- | --- |
| URL | `POST /api/ocr/pdf/analysis` |
| Params | `form`: body, `OcrPdf` entity; `template` is required and holds the PDF file content as Base64. |
| Returns | `ApiResult<List<PDFAnnotationMeta>>`: `data` is the annotation meta list resolved from the PDF. Fails when `template` is blank. |

### validateTime Validate time format

| Item | Content |
| --- | --- |
| URL | `GET /api/ocr/time/validate` |
| Params | `format`: query, string, required, time format (DateTimeFormatter pattern).<br />`timeZone`: query, string, conditionally required when `format` contains a time zone marker; when given, the current time is taken in that zone.<br />`locale`: query, string, optional, locale; defaults to the system locale. |
| Returns | `ApiResult<String>`: `data` is the current time rendered with the given format. |

## AdvanceOCRController — `/ocr/advance`

Advanced OCR: upload a PDF and submit a MinerU extraction task, supporting natural and AI extraction modes (synchronous, polls up to ~120 seconds).

### resolve Advanced OCR resolve

| Item | Content |
| --- | --- |
| URL | `POST /api/ocr/advance/resolve` |
| Params | `file`: form-data file field `file`, required, the PDF file.<br />`config`: form-data field `config`, optional, JSON string: `extractType` (`natural` / `ai`, default `natural`), `prompt` (extraction instruction for AI mode, default 「请按照我的描述提取需要的内容」). |
| Returns | `ApiResult<?>`: in `natural` mode `data` is the resolved content list JSON (the `content_list_v2.json` inside the MinerU result package); in `ai` mode `data` is the JSON text extracted by DeepSeek from the resolved `full.md` according to `prompt`. Fails on task submission errors, task failure, or polling timeout (~120 seconds). |

## OcrPdfController — `/ocr/pdf`

CRUD and select options for OCR PDF templates; a template can be maintained together with its child meta records.

### pageQuery Paged query

| Item | Content |
| --- | --- |
| URL | `POST /api/ocr/pdf/pageQuery` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiPagedResult`: `data` is a page of OCR PDF templates (`OcrPdf`). |

### query List query

| Item | Content |
| --- | --- |
| URL | `GET /api/ocr/pdf/query` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiResult<List<OcrPdf>>`: list of templates. |

### get Get details

| Item | Content |
| --- | --- |
| URL | `GET /api/ocr/pdf/get/{id}` |
| Params | `id`: path, string, template ID.<br />`hasMeta`: query, boolean, optional, true to include the child meta records. |
| Returns | `ApiResult<OcrPdf>`: template details; `metas` is an empty list unless `hasMeta` is true. |

### createOrUpdate Create or update

| Item | Content |
| --- | --- |
| URL | `POST /api/ocr/pdf/createOrUpdate` |
| Params | `form`: body, `OcrPdf` entity; non-empty `id` updates, empty `id` creates.<br />`hasMeta`: query, boolean, optional, true to save/update the child meta records in `form.metas` as well. |
| Returns | `ApiResult<OcrPdf>`: the saved template. |

### isDelete Delete template

| Item | Content |
| --- | --- |
| URL | `DELETE /api/ocr/pdf/isDelete/{id}` |
| Params | `id`: path, string, template ID. |
| Returns | `ApiResult<NullResult>`: `data` is empty; soft delete (enable status set to disabled). Fails if the template does not exist. |

### querySelectOptions Template select options

| Item | Content |
| --- | --- |
| URL | `GET /api/ocr/pdf/select/group` |
| Params | None |
| Returns | `ApiResult<List<SelectOptionGroup>>`: select options grouped by template (`title`); options are the meta records with configured rules under each template (`name`, value is the meta ID); `data` is empty when there is no data. |

## OcrPdfMetaController — `/ocr/pdf/meta`

CRUD and batch rule updates for OCR PDF template meta records.

### pageQuery Paged query

| Item | Content |
| --- | --- |
| URL | `POST /api/ocr/pdf/meta/pageQuery` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiPagedResult`: `data` is a page of template meta records (`OcrPdfMeta`). |

### query List query

| Item | Content |
| --- | --- |
| URL | `GET /api/ocr/pdf/meta/query` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiResult<List<OcrPdfMeta>>`: list of meta records. |

### get Get details

| Item | Content |
| --- | --- |
| URL | `GET /api/ocr/pdf/meta/get/{id}` |
| Params | `id`: path, string, meta record ID. |
| Returns | `ApiResult<OcrPdfMeta>`: meta record details. |

### createOrUpdate Create or update

| Item | Content |
| --- | --- |
| URL | `POST /api/ocr/pdf/meta/createOrUpdate` |
| Params | `form`: body, `OcrPdfMeta` entity; non-empty `id` updates, empty `id` creates. |
| Returns | `ApiResult<OcrPdfMeta>`: the saved meta record. |

### isDelete Delete meta

| Item | Content |
| --- | --- |
| URL | `DELETE /api/ocr/pdf/meta/isDelete/{id}` |
| Params | `id`: path, string, meta record ID. |
| Returns | `ApiResult<NullResult>`: `data` is empty; soft delete. Fails if the meta record does not exist. |

### updateMetaRules Batch update meta rules

| Item | Content |
| --- | --- |
| URL | `POST /api/ocr/pdf/meta/rule/{pdfId}` |
| Params | `pdfId`: path, string, template ID.<br />`params`: body, `Map<String,OcrPdfMeta>`, keyed by meta `name`, values provide the `rule`, `discernRule`, `floatAreaY`, and `lineHeight` to update. |
| Returns | `ApiResult<NullResult>`: `data` is empty; only updates the rule fields of existing meta records with matching names under the template, no insert or delete. |

## InvoiceOcrController — `/ocr/invoice`

Invoice OCR recognition aligned with GB/T 42965.1-2023: PDFs go through text-layer extraction, images through RapidOCR4j; item lists (detail lines) are parsed only when requested.

### recognize Recognize invoice file

| Item | Content |
| --- | --- |
| URL | `POST /api/ocr/invoice/recognize` |
| Params | `file`: form-data file field `file`, required, the invoice file (pdf/jpg/png, etc.).<br />`parseItems`: form-data field `parseItems`, boolean, optional, default false, whether to parse item lists (detail lines). |
| Returns | `ApiResult<InvoiceOcrResult>`: structured recognition result (invoice number, invoice date, total amount, remark, buyer/seller, etc.; standard fields in `InvoiceOcrResult`). Fails when the file is empty. |

### recognizeByFileId Recognize by attachment ID

| Item | Content |
| --- | --- |
| URL | `POST /api/ocr/invoice/recognize/fileId` |
| Params | `body`: body, `Map<String,Object>`, keys: `fileId` (required, invoice attachment ID; upload via `/upload/file` first to get one), `parseItems` (optional, default false, whether to parse item lists). |
| Returns | `ApiResult<InvoiceOcrResult>`: structured recognition result, same as above. Fails when `fileId` is blank. |

### health Engine health check

| Item | Content |
| --- | --- |
| URL | `POST /api/ocr/invoice/health` |
| Params | None |
| Returns | `ApiResult<Boolean>`: `true` means the invoice OCR engine is ready (missing dependencies count as not ready). |

## InvoiceOcrBackfillController — `/ocr/invoice`

One-off batch invoice verification tool: re-recognizes the four invoice fields (number/date/amount/remark) of two business tables row by row and compares them with stored values, only marking differences without modifying the database.

### backfill Batch invoice verification

| Item | Content |
| --- | --- |
| URL | `POST /api/ocr/invoice/backfill` |
| Params | None |
| Returns | `ApiResult<Map<String,Object>>`: `data` contains `batchNo` (batch number, yyyyMMddHHmmss) and `results` (one entry per verified table: `table`, `total`, `matched`, `mismatched`, `failed`, `mismatches` (difference details: `column`/`label`/`dbValue`/`ocrValue`), `failures` (recognition failures: `table`/`rowId`/`fileId`/`reason`)). The scope is fixed to rows with a non-empty `file_id` in `il_invoice_req_file` and `il_payment_order_file`; no update is executed at any point, so it is safe to re-run. |
