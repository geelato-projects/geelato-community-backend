---
title: API · Excel 导入导出
sidebar_label: Excel 导入导出
---
# Excel 导入导出（excel）

3 个 Controller，共 17 个端点。标注 ⚙ 的为设计态接口。

## ExportExcelController — `/export/file`

导出文件列表查询，以及按模板或按列元数据生成导出文件（Excel/Word 等），支持下载与 PDF 转换。

### pageQuery 导出文件分页

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/export/file/list` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：data 为附件分页列表（`Attach` 实体，即 `platform_attach` 表记录）。 |

### exportWps 按模板导出文件

| 项 | 内容 |
| --- | --- |
| URL | `POST\|GET /api/export/file/{dataType}/{templateId}` |
| 参数 | `dataType`：path，string，数据解析类型，支持 `mql`、`data`；`data` 时从请求体 gql JSON 解析数据，其余类型报错。<br />`templateId`：path，string，导出模板 ID。<br />`index`：query，string，可选，模板索引（1-19），默认 1。<br />`fileName`：query，string，可选，导出文件名（缺省为 `模板名_时间戳`，自动追加模板扩展名）。<br />`markText`：query，string，可选，水印文本。<br />`markKey`：query，string，可选，水印样式配置键（按系统配置查询水印样式）。<br />`readonly`：query，boolean，导出文件是否只读。<br />`isDownload`：query，boolean，true 时将文件写入响应下载。<br />`isPdf`：query，boolean，true 时转换为 PDF 并删除原始导出文件。<br />body（`dataType=data` 时）：gql JSON，含 `valueMapList`（`List<Map>`，多行数据）与 `valueMap`（Map，单条数据）。 |
| 返回 | `ApiResult<Attachment>`：data 为导出生成的附件记录；`isDownload=true` 时直接写响应流（无 JSON body，content-type 与文件名取自附件）；`isPdf=true` 时 data 为 PDF 附件记录。 |

### exportWps 按列元数据导出文件

| 项 | 内容 |
| --- | --- |
| URL | `POST\|GET /api/export/file/column/meta/list` |
| 参数 | `appId`：query，string，可选，应用 ID，缺省取请求头 `appId`。<br />`fileName`：query，string，可选，导出文件名（缺省为 `exportExcelByColumnMeta_时间戳.xlsx`）。<br />`markText`：query，string，可选，水印文本。<br />`markKey`：query，string，可选，水印样式配置键。<br />`readonly`：query，boolean，导出文件是否只读。<br />`isDownload`：query，boolean，true 时将文件写入响应下载。<br />`isPdf`：query，boolean，true 时转换为 PDF 并删除原始导出文件。<br />body：gql JSON，含 `valueMapList`（`List<Map>`，多行数据）、`valueMap`（Map，单条数据）、`column`（`List<ExportColumn>`，表头列定义，必填）、`meta`（`List<PlaceholderMeta>`，占位符数据定义，必填）。 |
| 返回 | `ApiResult<Attachment>`：data 为导出生成的附件记录；`isDownload=true` 时直接写响应流（无 JSON body，content-type 与文件名取自附件）；`isPdf=true` 时 data 为 PDF 附件记录。 |

### toPdf 导出文件转 PDF

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/export/file/toPdf/{id}` |
| 参数 | `id`：path，string，Excel 导出文件附件 ID。 |
| 返回 | `ApiResult<String>`：data 为转换后的 PDF 附件 ID；文件不存在、附件信息不存在或转换失败时返回失败。 |

## ExportTemplateController — `/export/template`

导出/导入模板（`ExportTemplate`）管理：分页与列表查询、复制、保存、删除、模板文件生成与模板内容读取。

### pageQuery 模板分页查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/export/template/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：data 为模板分页列表（`ExportTemplate`）。 |

### query 模板列表查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/export/template/query` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<List<ExportTemplate>>`：data 为模板列表（按排序参数排序）。 |

### get 模板详情

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/export/template/get/{id}` |
| 参数 | `id`：path，string，模板 ID。 |
| 返回 | `ApiResult<ExportTemplate>`：data 为模板详情。 |

### copy 复制模板

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/export/template/copy/{id}` |
| 参数 | `id`：path，string，被复制模板 ID。 |
| 返回 | `ApiResult<ExportTemplate>`：data 为新模板记录（标题追加 ` - 副本`，重新生成 ID）。 |

### createOrUpdate 保存模板

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/export/template/createOrUpdate` |
| 参数 | `form`：body，`ExportTemplate` 实体；`id` 为空创建，非空更新；保存后自动生成 `template` 模板文件。 |
| 返回 | `ApiResult<ExportTemplate>`：data 为保存后的模板记录。 |

### isDelete 删除模板

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/export/template/isDelete/{id}` |
| 参数 | `id`：path，string，模板 ID；模板不存在时报错。 |
| 返回 | `ApiResult<NullResult>`：置 `enableStatus` 为停用后删除记录，`data` 为空。 |

### generateFile 生成模板文件

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/export/template/generateFile/{id}` |
| 参数 | `id`：path，string，模板 ID。<br />`fileType`：body，string，生成文件类型，支持 `template`（导入模板文件）、`meta`（元数据/规则文件），可逗号分隔多值。 |
| 返回 | `ApiResult<Attachment>`：data 为生成的附件记录；参数缺失或模板不存在时返回失败。 |

### indexTemplate 模板索引清单

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/export/template/index/{id}` |
| 参数 | `id`：path，string，模板 ID。 |
| 返回 | `ApiResult<Map<Integer,Object>>`：data 为模板索引（1-9）到模板文件名的映射，仅含非空模板；模板不存在时返回空映射。 |

### indexTemplate 模板内容读取

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/export/template/base64/{id}/{index}` |
| 参数 | `id`：path，string，模板 ID。<br />`index`：path，string，模板索引（1-19）。 |
| 返回 | `ApiResult<String>`：data 为该索引对应的模板内容字符串（Base64Helper JSON，含文件名与 base64 文件内容）；模板不存在时返回失败，索引超出范围抛异常。 |

## ImportExcelController — `/import`

Excel 数据导入：导入模板获取、附件/上传文件导入与复杂模板解析。

### getTemplate 获取导入模板

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/import/template/{templateId}` |
| 参数 | `templateId`：path，string，导入模板 ID。 |
| 返回 | `ApiResult<ExportTemplate>`：data 为导入模板记录。 |

### importAttach 按附件导入

| 项 | 内容 |
| --- | --- |
| URL | `POST\|GET /api/import/attach/{importType}/{templateId}/{attachId}` |
| 参数 | `importType`：path，string，导入类型：`part` 部分导入 / `all` 全部导入（出错中断并回滚）。<br />`templateId`：path，string，导入模板 ID。<br />`attachId`：path，string，业务数据 Excel 文件附件 ID。<br />`index`：query，string，可选，模板索引。 |
| 返回 | `ApiResult`：成功时 data 为本次导入事务 UUID（string），`msg` 为导入数量统计；数据校验失败时 data 为错误说明文件附件（`Attachment`），详情见错误文件。 |

### importFile 按上传导入

| 项 | 内容 |
| --- | --- |
| URL | `POST\|GET /api/import/file/{importType}/{templateId}` |
| 参数 | `importType`：path，string，导入类型：`part` 部分导入 / `all` 全部导入（出错中断并回滚）。<br />`templateId`：path，string，导入模板 ID。<br />`index`：query，string，可选，模板索引。<br />body：form-data 文件字段 `file`，上传的业务数据 Excel（.xls/.xlsx）。 |
| 返回 | `ApiResult`：成功时 data 为本次导入事务 UUID（string），`msg` 为导入数量统计；数据校验失败时 data 为错误说明文件附件（`Attachment`），详情见错误文件。 |

### importComplexExcel 复杂模板解析

| 项 | 内容 |
| --- | --- |
| URL | `POST\|GET /api/import/complex/{importType}/{templateId}/{attachId}` |
| 参数 | `importType`：path，string，导入类型（保留参数，兼容接口签名）。<br />`templateId`：path，string，导入模板 ID。<br />`attachId`：path，string，业务数据 Excel 文件附件 ID。 |
| 返回 | `ApiResult<Map<String,Object>>`：data 为解析后的扁平化 JSON（不入库）：固定单元格字段与列表区域平铺同层级，列表键为列表 `fieldName`，值为行数据数组。 |
