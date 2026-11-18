---
title: API · 文件与附件
sidebar_label: 文件与附件
---
# 文件与附件（file）

3 个 Controller，共 21 个端点。标注 ⚙ 的为设计态接口。

## AttachController — `/attach`

附件记录管理：复制、引用、查询、更新、替换、有效性校验、存储方式迁移、删除与 Excel 转 PDF。

### copy 复制附件

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/attach/copy/{id}` |
| 参数 | `id`：path，string，被复制附件 ID。<br />`genre`：body，string，可选，业务类型标记（写入新附件 `genre`）。<br />`name`：body，string，可选，新文件名（自动沿用原扩展名）。<br />`invalidTime`：body，string，可选，失效时间（DATETIME 格式）。 |
| 返回 | `ApiResult<Attachment>`：data 为复制生成的新附件记录；附件或文件不存在时返回失败。 |

### quote 引用附件

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/attach/quote/{id}` |
| 参数 | `id`：path，string，被引用附件 ID。<br />`genre`：body，string，可选，业务类型标记。<br />`name`：body，string，可选，新文件名（自动沿用原扩展名）。<br />`invalidTime`：body，string，可选，失效时间（DATETIME 格式）。 |
| 返回 | `ApiResult<Attachment>`：data 为新引用附件记录（文件路径与源附件一致，`pid` 指向源附件）；源附件不存在时返回失败。 |

### get 附件详情

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/attach/get/{id}` |
| 参数 | `id`：path，string，附件 ID。 |
| 返回 | `ApiResult<Attachment>`：data 为附件记录。 |

### image 子附件列表

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/attach/image/{id}` |
| 参数 | `id`：path，string，父附件 ID（`pid`）。 |
| 返回 | `ApiResult<List<Attachment>>`：data 为该 `pid` 下的附件列表（如图片的缩略图）。 |

### update 更新附件

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/attach/update/{id}` |
| 参数 | `id`：path，string，附件 ID。<br />body：`Map<String,Object>`，附件字段名到值的映射（填充到 `Attachment` 字段）。 |
| 返回 | `ApiResult<Attachment>`：data 为更新后的附件记录。 |

### replace 替换附件

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/attach/replace/{sourceId}/{targetId}` |
| 参数 | `sourceId`：path，string，源附件 ID。<br />`targetId`：path，string，目标附件 ID。<br />body：`Map<String,Object>`，目标附件字段更新值。 |
| 返回 | `ApiResult<Attachment>`：data 为目标附件记录（已更新字段、继承源附件 `pid` 并标记 `Replace`，ID 替换为源附件 ID；源附件记录改用新 ID 保留）；源或目标附件不存在时返回失败。 |

### validate 校验文件存在

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/attach/valid` |
| 参数 | `attachmentIds`：body，string，逗号分隔附件 ID。 |
| 返回 | `ApiResult<Map<String,Boolean>>`：data 为附件 ID 到文件是否存在（布尔）的映射；ID 为空或附件不存在时返回失败。 |

### updateStorageType 迁移存储方式

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/attach/storage/{type}` |
| 参数 | `type`：path，string，目标存储类型：`local` 本地存储 / `aliyun` 阿里云 OSS。<br />`attachmentIds`：body，string，逗号分隔附件 ID。 |
| 返回 | `ApiResult<Map<String,String>>`：data 为附件 ID 到处理信息的映射，仅含异常项（文件不存在、迁移失败、存储方式未变化）；ID 为空或附件不存在时返回失败。 |

### list 附件列表查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/attach/list` |
| 参数 | body：`Map<String,Object>`，查询条件与分页参数（如 `ids`、`pids`、`batchNo`、`storageType`，分页键 `current`/`pageSize`/`order`，见 `FileHandler.getAttachments`）。 |
| 返回 | `ApiResult<List<Attachment>>`：data 为符合条件的附件列表。 |

### pageQuery 附件分页查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/attach/pageQuery` |
| 参数 | `current`：body，long，页码，默认 1。<br />`pageSize`：body，int，每页数量，默认 10。<br />`order`：body，string，排序（`字段\|方向`，`\|` 替换为空格，默认 `create_at desc`）。<br />其余键：body，作为查询条件。 |
| 返回 | `ApiPagedResult`：data 为 `DataItems<Attachment>`（`items` 附件数组 + `total` 总数），外层含分页信息。 |

### columnType 列可选值

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/attach/column/{type}` |
| 参数 | `type`：path，string，取值列类型：`source`、`storageType`（枚举值直接返回）、`type`、`resolution`（查询表内已有值）。 |
| 返回 | `ApiResult<List<String>>`：data 为该列的可选值列表。 |

### remove 删除附件

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/attach/remove/{id}` |
| 参数 | `id`：path，string，附件 ID。<br />`isRemoved`：query，boolean，是否真正从数据库删除（true 删除，false 标记删除）。<br />`deleteOss`：query，boolean，可选，默认 false，是否联动删除 OSS 对象。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空。 |

### batchRemove 批量删除附件

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/attach/remove/{type}` |
| 参数 | `type`：path，string，删除范围；`batchNo` 时按附件 ID 找到批次号并扩展删除同批次全部附件。<br />`isRemoved`：query，boolean，是否真正从数据库删除。<br />`attachmentIds`：body，string，逗号分隔附件 ID。 |
| 返回 | `ApiResult<NullResult>`：逐个删除附件，`data` 为空；ID 为空时返回失败。 |

### toPdf 附件转 PDF

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/attach/toPdf/{id}` |
| 参数 | `id`：path，string，附件 ID；仅支持 .xlsx/.xls 文件。 |
| 返回 | `ApiResult<String>`：data 为转换后的 PDF 附件 ID；附件不存在、文件不存在、格式不支持或转换失败时返回失败。 |

## CompressController — `/pack`

附件打包压缩（zip 分包）与压缩包附件 ID 解析，支持按附件 ID、批次号与缩略图压缩。

### byFileIds 按附件 ID 压缩

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/pack/by/fileIds` |
| 参数 | body：`CompressRequestBody` 结构：`attachmentIds`（string，逗号分隔附件 ID）、`batchNos`（string，逗号分隔批次号）、`fileName`（string，压缩包文件名）、`serviceType`（string，存储服务类型）、`genre`（string，业务类型标记）、`validDuration`（int，有效时长，单位秒，设置后自动计算失效时间）、`invalidTime`（date，失效时间）、`amount`（int，单个压缩包最大文件数，缺省用系统默认）、`gql`（string，gql 语句）。 |
| 返回 | `ApiResult<Map<String,Object>>`：data 含 `attachmentIds`（压缩包附件 ID 列表）与 `batchNo`（批次号）；压缩失败时返回失败。 |

### byBatchNos 按批次号压缩

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/pack/by/batch` |
| 参数 | body：同 `by/fileIds` 的 `CompressRequestBody` 结构。 |
| 返回 | `ApiResult<Map<String,Object>>`：data 含 `attachmentIds`（压缩包附件 ID 列表）与 `batchNo`（批次号）；压缩失败时返回失败。 |

### downloadFiles 打包或解析附件

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/pack/by/{type}/{column}` |
| 参数 | `type`：path，string，`pack` 打包压缩 / `simple` 仅解析附件 ID。<br />`column`：path，string，`id` 按附件 ID / `batchNo` 按附件 ID 查批次号并扩展为同批次附件。<br />`attachmentIds`：body，string，逗号分隔附件 ID。 |
| 返回 | `ApiResult<List<String>>`：data 为附件 ID 列表（`pack` 时为压缩包附件 ID，文件名取当前时间戳）；附件不存在或压缩失败时抛错。 |

### byThumbs 压缩缩略图

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/pack/by/thumb` |
| 参数 | body：同 `by/fileIds` 的 `CompressRequestBody` 结构；`attachmentIds` 按 `pid` 解析为其子附件（缩略图）ID 后再压缩。 |
| 返回 | `ApiResult<Map<String,Object>>`：data 含 `attachmentIds`（压缩包附件 ID 列表）与 `batchNo`（批次号）；压缩失败时返回失败。 |

## OssController — `/oss`

OSS（阿里云对象存储）管理：Bucket 统计查询与数据库-云端双边文件核对（异步任务）。仅当 OSS 组件启用时注册。

### stats Bucket 统计查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/oss/stats` |
| 参数 | 无 |
| 返回 | `ApiResult<Map<String,Object>>`：data 为 OSS Bucket 统计信息（存储占用、文件数量等）；获取失败时返回失败。 |

### startVerify 启动核对任务

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/oss/start-verify` |
| 参数 | 无 |
| 返回 | `ApiResult<Map<String,Object>>`：data 含 `taskId`；异步执行核对，任务结果保留 30 分钟，通过 `verify-status` 轮询进度。 |

### verifyStatus 核对任务进度

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/oss/verify-status/{taskId}` |
| 参数 | `taskId`：path，string，核对任务 ID。 |
| 返回 | `ApiResult<Map<String,Object>>`：data 含 `status`（`running`/`completed`/`failed`）、`total`、`checked`、`existCount`、`missingCount`、`results`（每项含 `attachId`、`name`、`objectName`、`type`、`exists`，校验异常时含 `error`），失败时含 `errorMessage`；任务不存在或已过期时返回失败。 |
