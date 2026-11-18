---
title: API · 条码
sidebar_label: 条码
---
# 条码（zxing）

1 个 Controller，共 9 个端点。标注 ⚙ 的为设计态接口。

## BarcodeController — `/zxing/barcode`

条码配置的增删改查、编码唯一性校验、按配置生成条码图片与可用字体查询。

### pageQuery 分页查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/zxing/barcode/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：`data` 为条码配置（`Barcode`）分页数据。 |

### query 列表查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/zxing/barcode/query` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<List<Barcode>>`：条码配置列表。 |

### get 查询详情

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/zxing/barcode/get/{id}` |
| 参数 | `id`：path，string，条码配置 ID。 |
| 返回 | `ApiResult<Barcode>`：条码配置详情。 |

### createOrUpdate 新建或更新

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/zxing/barcode/createOrUpdate` |
| 参数 | `form`：body，`Barcode` 实体；`id` 非空更新、为空新建（保存前执行 `afterSet` 默认值处理）。 |
| 返回 | `ApiResult<Barcode>`：保存后的条码配置。 |

### isDelete 删除条码配置

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/zxing/barcode/isDelete/{id}` |
| 参数 | `id`：path，string，条码配置 ID。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空；软删除。配置不存在时报错。 |

### validate 校验编码唯一性

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/zxing/barcode/validate` |
| 参数 | `form`：body，`Barcode` 实体，提供待校验的 `code`、`id`、`tenantCode`（`tenantCode` 为空时取当前会话租户）。 |
| 返回 | `ApiResult<Boolean>`：`true` 表示校验通过（`platform_barcode` 表中无同 `code` 的未删除记录，排除自身）；`false` 表示存在重复。 |

### generate 按配置生成条码

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/zxing/barcode/generate/{type}/{id}/{text}` |
| 参数 | `type`：path，string，输出方式：`attach`（存为附件）、`base64`（Base64 字符串）、其它值返回图片文件保存路径（不区分大小写）。<br />`id`：path，string，条码配置 ID。<br />`text`：path，string，条码内容文本。 |
| 返回 | `ApiResult<?>`：`type=attach` 时 `data` 为保存后的附件记录（`Map<String,Object>`，genre 为 `Barcode`）；`type=base64` 时为 `data:[contentType];base64,[...]` 格式字符串；其它 `type` 时 `data` 为生成的条码图片保存路径。条码配置不存在时报错。 |

### generate 免配置生成条码

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/zxing/barcode/generate/{type}/{text}` |
| 参数 | `type`：path，string，输出方式，同上。<br />`text`：path，string，条码内容文本。<br />`form`：body，`Barcode` 实体，条码配置（保存前执行 `afterSet` 默认值处理）。 |
| 返回 | `ApiResult<?>`：内容同上，按 `type` 决定。 |

### getFonts 可用字体列表

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/zxing/barcode/getFonts` |
| 参数 | 无 |
| 返回 | `ApiResult<List<String>>`：服务器可用字体族名称列表（结果缓存于 Redis，7 天过期）。 |
