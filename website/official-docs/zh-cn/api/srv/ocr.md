---
title: API · OCR 识别
sidebar_label: OCR 识别
---
# OCR 识别（ocr）

6 个 Controller，共 24 个端点。标注 ⚙ 的为设计态接口。

## OCRController — `/ocr`

PDF 识别的模板配套接口：解析 PDF 注释元数据、按模板提取或清除内容、规则测试与时间格式校验。

### meta 解析注释元数据

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/ocr/pdf/meta/{fileId}` |
| 参数 | `fileId`：path，string，PDF 文件附件 ID。 |
| 返回 | `ApiResult<List<PDFAnnotationMeta>>`：`data` 为该 PDF 解析出的注释元数据列表（由 OCR 插件解析）。 |

### meta 简版模板解析

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/ocr/pdf/resolve0` |
| 参数 | `fileId`：query，string，PDF 文件附件 ID。<br />`templateId`：query，string，OCR PDF 模板 ID。<br />`wholeContent`：query，boolean，可选，默认 false，是否返回整个 PDF 解析内容。 |
| 返回 | `wholeContent=true` 时 `data` 为 `PDFResolveData`（按模板元数据解析出的 PDF 全文数据）；否则为 `List<PDFAnnotationPickContent>`（按注释位置拾取的内容列表）。文件不存在或模板、模板元数据为空时报错。 |

### meta 清除PDF区域内容

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/ocr/pdf/content/clear` |
| 参数 | `fileId`：query，string，PDF 文件附件 ID。<br />`annotationPositionMetaList`：body，`List<AnnotationPositionMeta>`，要清除内容的注释位置区域列表。 |
| 返回 | `ApiResult<String>`：`data` 为清除内容后另存 PDF 的新附件 ID（genre 为 `clearPdf`）。 |

### metaResolve 按模板解析PDF

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/ocr/pdf/resolve` |
| 参数 | `fileId`：query，string，PDF 文件附件 ID。<br />`templateId`：query，string，OCR PDF 模板 ID，支持逗号分隔多个模板，逐个尝试匹配。<br />`wholeContent`：query，boolean，可选，默认 false，是否返回整个 PDF 解析内容。 |
| 返回 | 模板匹配成功时，`wholeContent=true` 返回 `ApiResult<OcrPdfWhole>`（含格式化后的内容），否则返回 `ApiResult<List<OcrPdfContent>>`（格式化后的字段内容列表）；所有模板均不匹配时返回失败（`Template matching failure`）。候选模板需先通过全文正则校验。 |

### ruleTest 模板规则测试

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/ocr/pdf/meta/test` |
| 参数 | `data`：body，`Map<String,Object>`，键：`content`（待处理内容字符串）、`rule`（规则 JSON 数组字符串，解析为 `List<OcrPdfMetaRule>`）、`metas`（元数据 JSON 数组字符串，解析为 `List<OcrPdfContent>`）。 |
| 返回 | `ApiResult<String>`：`data` 为按规则处理后的内容字符串。 |

### analysis 解析模板PDF元数据

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/ocr/pdf/analysis` |
| 参数 | `form`：body，`OcrPdf` 实体，`template` 必填，为 PDF 文件的 Base64 内容。 |
| 返回 | `ApiResult<List<PDFAnnotationMeta>>`：`data` 为该 PDF 解析出的注释元数据列表。`template` 为空时报错。 |

### validateTime 时间格式校验

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/ocr/time/validate` |
| 参数 | `format`：query，string，必填，时间格式（DateTimeFormatter pattern）。<br />`timeZone`：query，string，条件必填，`format` 含时区标记时必填；给定时区则按该时区取当前时间。<br />`locale`：query，string，可选，地区，缺省用系统默认。 |
| 返回 | `ApiResult<String>`：`data` 为按格式渲染的当前时间字符串。 |

## AdvanceOCRController — `/ocr/advance`

高级 OCR：上传 PDF 提交 MinerU 解析任务，支持自然提取与 AI 提取两种模式（同步等待，轮询最长约 120 秒）。

### resolve 高级OCR解析

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/ocr/advance/resolve` |
| 参数 | `file`：form-data 文件字段 `file`，必填，PDF 文件。<br />`config`：form-data 字段 `config`，可选，JSON 字符串：`extractType`（`natural` 自然提取 / `ai` AI 提取，缺省 `natural`）、`prompt`（AI 模式的提取要求，缺省「请按照我的描述提取需要的内容」）。 |
| 返回 | `ApiResult<?>`：`natural` 模式 `data` 为解析出的内容清单 JSON（MinerU 结果包内 `content_list_v2.json` 的内容）；`ai` 模式 `data` 为按 `prompt` 提取的 JSON 文本（DeepSeek 基于解析结果 `full.md` 返回）。任务提交失败、任务失败或轮询超时（约 120 秒）返回失败。 |

## OcrPdfController — `/ocr/pdf`

OCR PDF 模板的增删改查与下拉选项，模板可连同子表元数据一并维护。

### pageQuery 分页查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/ocr/pdf/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：`data` 为 OCR PDF 模板（`OcrPdf`）分页数据。 |

### query 列表查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/ocr/pdf/query` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<List<OcrPdf>>`：模板列表。 |

### get 查询详情

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/ocr/pdf/get/{id}` |
| 参数 | `id`：path，string，模板 ID。<br />`hasMeta`：query，boolean，可选，true 时返回子表元数据。 |
| 返回 | `ApiResult<OcrPdf>`：模板详情；`hasMeta` 不为 true 时 `metas` 为空列表。 |

### createOrUpdate 新建或更新

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/ocr/pdf/createOrUpdate` |
| 参数 | `form`：body，`OcrPdf` 实体；`id` 非空更新、为空新建。<br />`hasMeta`：query，boolean，可选，true 时连同 `form.metas` 子表数据一并保存/更新。 |
| 返回 | `ApiResult<OcrPdf>`：保存后的模板。 |

### isDelete 删除模板

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/ocr/pdf/isDelete/{id}` |
| 参数 | `id`：path，string，模板 ID。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空；软删除（启用状态置为禁用）。模板不存在时报错。 |

### querySelectOptions 模板下拉选项

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/ocr/pdf/select/group` |
| 参数 | 无 |
| 返回 | `ApiResult<List<SelectOptionGroup>>`：按模板（`title`）分组的下拉选项，选项取该模板下已配置规则的元数据（`name`，value 为元数据 ID）；无数据时 `data` 为空。 |

## OcrPdfMetaController — `/ocr/pdf/meta`

OCR PDF 模板元数据的增删改查与规则批量更新。

### pageQuery 分页查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/ocr/pdf/meta/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：`data` 为模板元数据（`OcrPdfMeta`）分页数据。 |

### query 列表查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/ocr/pdf/meta/query` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<List<OcrPdfMeta>>`：元数据列表。 |

### get 查询详情

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/ocr/pdf/meta/get/{id}` |
| 参数 | `id`：path，string，元数据 ID。 |
| 返回 | `ApiResult<OcrPdfMeta>`：元数据详情。 |

### createOrUpdate 新建或更新

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/ocr/pdf/meta/createOrUpdate` |
| 参数 | `form`：body，`OcrPdfMeta` 实体；`id` 非空更新、为空新建。 |
| 返回 | `ApiResult<OcrPdfMeta>`：保存后的元数据。 |

### isDelete 删除元数据

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/ocr/pdf/meta/isDelete/{id}` |
| 参数 | `id`：path，string，元数据 ID。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空；软删除。元数据不存在时报错。 |

### updateMetaRules 批量更新规则

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/ocr/pdf/meta/rule/{pdfId}` |
| 参数 | `pdfId`：path，string，模板 ID。<br />`params`：body，`Map<String,OcrPdfMeta>`，键为元数据 `name`，值提供要更新的 `rule`、`discernRule`、`floatAreaY`、`lineHeight`。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空；仅更新该模板下已存在的同名元数据的规则字段，不新增、不删除。 |

## InvoiceOcrController — `/ocr/invoice`

发票 OCR 识别，输出对齐 GB/T 42965.1-2023；PDF 走文本层提取、图片走 RapidOCR4j 识别，项目清单（明细行）仅在调用方要求时解析。

### recognize 发票文件识别

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/ocr/invoice/recognize` |
| 参数 | `file`：form-data 文件字段 `file`，必填，发票文件（pdf/jpg/png 等）。<br />`parseItems`：form-data 字段 `parseItems`，boolean，可选，默认 false，是否解析项目清单（明细行）。 |
| 返回 | `ApiResult<InvoiceOcrResult>`：结构化识别结果（发票号码、开票日期、开票金额、备注、购销方等，标准字段见 `InvoiceOcrResult`）。文件为空返回失败。 |

### recognizeByFileId 按附件识别

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/ocr/invoice/recognize/fileId` |
| 参数 | `body`：body，`Map<String,Object>`，键：`fileId`（必填，发票附件 ID，可先用 `/upload/file` 上传获取）、`parseItems`（可选，默认 false，是否解析项目清单）。 |
| 返回 | `ApiResult<InvoiceOcrResult>`：结构化识别结果，同上。`fileId` 为空返回失败。 |

### health 引擎健康检查

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/ocr/invoice/health` |
| 参数 | 无 |
| 返回 | `ApiResult<Boolean>`：`true` 表示发票 OCR 引擎就绪（依赖缺失视为未就绪）。 |

## InvoiceOcrBackfillController — `/ocr/invoice`

批量发票核对一次性工具：对两张业务表的发票四字段（号码/日期/金额/备注）逐行重新识别并与库值比对，只标记差异、不修改数据库。

### backfill 批量发票核对

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/ocr/invoice/backfill` |
| 参数 | 无 |
| 返回 | `ApiResult<Map<String,Object>>`：`data` 含 `batchNo`（批次号，yyyyMMddHHmmss）与 `results`（每张被核对表一条：`table`、`total`、`matched`、`mismatched`、`failed`、`mismatches`（差异明细：`column`/`label`/`dbValue`/`ocrValue`）、`failures`（识别失败记录：`table`/`rowId`/`fileId`/`reason`））。核对范围固定为 `il_invoice_req_file`、`il_payment_order_file` 两表中 `file_id` 非空的行，全程不执行任何更新，可安全重跑。 |
