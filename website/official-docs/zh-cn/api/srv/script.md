---
title: API · 脚本与外部服务
sidebar_label: 脚本与外部服务
---
# 脚本与外部服务（script）

5 个 Controller，共 16 个端点。本域全部为设计态接口。

## ApiController ⚙ — `/script/api`

脚本 API（`Api`，对应表 platform_api）的定义维护、查询与编码校验。

### pageQuery 分页查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/script/api/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：`data` 为脚本 API（`Api`）分页数据。 |

### query 列表查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/script/api/query` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<List<Api>>`：脚本 API 列表。 |

### queryGroupName 分组名列表

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/script/api/queryGroupName` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<List<String>>`：查询结果中去重后的 `groupName`（空白值不计入）。 |

### get 单条详情

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/script/api/get/{id}` |
| 参数 | `id`：path，string，脚本 API 主键，必填。 |
| 返回 | `ApiResult<Api>`：`data` 为单条脚本 API，并按参数备选类型（`REQUEST`/`RESPONSE`）附带 `requestParams`、`responseParams` 两个参数列表。 |

### createOrUpdate 新建或更新

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/script/api/createOrUpdate` |
| 参数 | `form`：body，`Api` 实体；`id` 为空时新建、非空时更新。代码中引用的字段包括 `id`、`code`、`appId`、`tenantCode`、`groupName`、`outsideUrl`、`requestParams`、`responseParams`，其余字段见实体定义。 |
| 返回 | `ApiResult<Api>`：`data` 为新建或更新后的脚本 API。 |

### isDelete 软删除

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/script/api/isDelete/{id}` |
| 参数 | `id`：path，string，脚本 API 主键，必填。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空；记录不存在时返回失败。 |

### validate 编码唯一性校验

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/script/api/validate` |
| 参数 | `form`：body，`Api` 实体，仅取 `id`、`code`、`appId`、`tenantCode`。 |
| 返回 | `ApiResult<Boolean>`：校验表 platform_api 中该 `code` 是否可用（排除自身 `id`，仅统计未删除记录，`tenant_code` 缺省时取会话租户），`true` 表示无冲突。 |

### generateOutside 生成外部地址

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/script/api/generateOutside` |
| 参数 | `form`：body，`Api` 实体，仅取 `id`（生成时排除自身的 `outsideUrl` 参与查重）。 |
| 返回 | `ApiResult<String>`：`data` 为以 `/` 开头的 10 位随机外部路径，已与现有脚本 API 的 `outsideUrl` 去重。 |

## ApiParamController ⚙ — `/script/api/param`

脚本 API 参数（`ApiParam`）的查询与维护。

### pageQuery 分页查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/script/api/param/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：`data` 为脚本 API 参数（`ApiParam`）分页数据。 |

### query 列表查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/script/api/param/query` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult`：`data` 为脚本 API 参数（`ApiParam`）列表（方法签名未声明泛型，按实际返回标注）。 |

### get 单条详情

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/script/api/param/get/{id}` |
| 参数 | `id`：path，string，参数主键，必填。 |
| 返回 | `ApiResult`：`data` 为单条脚本 API 参数（`ApiParam`）。 |

### createOrUpdate 新建或更新

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/script/api/param/createOrUpdate` |
| 参数 | `form`：body，`ApiParam` 实体；`id` 为空时新建、非空时更新。 |
| 返回 | `ApiResult`：`data` 为新建或更新后的脚本 API 参数。 |

### isDelete 软删除

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/script/api/param/isDelete/{id}` |
| 参数 | `id`：path，string，参数主键，必填。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空；记录不存在时返回失败。 |

## ExtServiceController ⚙ — `/ext`

对外发布的脚本 API 统一入口：按外部地址匹配并执行脚本，端点标注 `@IgnoreVerify` 跳过平台接口验证。

### exec 外部地址执行脚本

| 项 | 内容 |
| --- | --- |
| URL | `GET\|POST /api/ext/{outside_url}` |
| 参数 | `outside_url`：path，string，脚本 API 的外部访问地址（对应 `Api.outsideUrl`）。<br />body：string，原始请求体文本，作为脚本入参（GET 请求时请求体为空）。 |
| 返回 | `ApiResult`：`data` 为脚本执行结果；若脚本 API 配置 `responseFormat=custom`，则直接返回结果 JSON 字符串（不包 `ApiResult`）。执行失败返回失败信息；按 API 的 `logLevel`（含 `dg`）写 platform_api_log 调试日志。 |

## GraalServiceController ⚙ — `/script/function`

输出 GraalVM 中已注册的函数服务描述。

### getGraalDescriptions 函数描述列表

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/script/function/list` |
| 参数 | 无 |
| 返回 | `ApiResult`：`data` 为 GraalVM 函数服务描述列表（来自 GraalManager 注册信息）。 |

## ServiceController ⚙ — `/service`

平台内部脚本执行入口：按脚本 ID 执行脚本。

### exec 按编号执行脚本

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/service/exec/{scriptId}` |
| 参数 | `scriptId`：path，string，内部脚本 ID，必填。<br />body：string，原始请求体文本，作为脚本入参。 |
| 返回 | `ApiResult`：`data` 为脚本执行结果；执行失败返回失败信息。 |
