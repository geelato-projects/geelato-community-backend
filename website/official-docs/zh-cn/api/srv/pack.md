---
title: API · 应用打包
sidebar_label: 应用打包
---
# 应用打包（pack）

2 个 Controller，共 12 个端点。标注 ⚙ 的为设计态接口。

## AppVersionController — `/app/version`

应用版本管理：版本分页查询、详情、新建或更新、删除、版本号唯一性校验与版本包数据读取。

### pageQuery 分页查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/app/version/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md)。 |
| 返回 | `ApiPagedResult`：data 为应用版本分页数据（外层含分页信息）。 |

### isDelete 删除版本

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/app/version/isDelete/{id}` |
| 参数 | `id`：path，string，版本 ID。 |
| 返回 | `ApiResult<NullResult>`：标记删除版本，`data` 为空；版本不存在时返回失败。 |

### get 版本详情

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/app/version/get/{id}` |
| 参数 | `id`：path，string，版本 ID。 |
| 返回 | `ApiResult<AppVersion>`：data 为应用版本记录。 |

### getPackage 读取版本包数据

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/app/version/package/{id}` |
| 参数 | `id`：path，string，版本 ID。 |
| 返回 | `ApiResult<AppPackData>`：data 为版本包的应用包数据（从 `.zgdp` 压缩包或包文件中读取 `.gdp` 内容解析为 `AppPackData`）；版本不存在、包路径缺失、包文件缺失或读取失败时抛出 `PackException`（60xxx 错误码）。 |

### createOrUpdate 新建或更新版本

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/app/version/createOrUpdate` |
| 参数 | body：`AppVersion` 结构；`id` 为空时新建，否则更新。 |
| 返回 | `ApiResult<AppVersion>`：data 为新建/更新后的版本记录。 |

### validate 校验版本号唯一

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/app/version/validate` |
| 参数 | body：`AppVersion` 结构，取 `id`、`version`、`appId`、`tenantCode` 参与校验。 |
| 返回 | `ApiResult<Boolean>`：data 为 `version` 在同应用、同租户且未删除的版本中是否唯一（排除自身 `id`）。 |

## PackageController — `/package`

该 Controller 为字面 `@Controller`（类级 `@RequestMapping("/package")`），不享受 `@ApiRestController` 家族的 `/api` 自动前缀，以下 URL 均无 `/api` 前缀。

应用打包与部署：打包应用、合并打包、版本包上传与下载、部署版本包与回滚。

### packetApp 打包应用

| 项 | 内容 |
| --- | --- |
| URL | `GET\|POST /package/packet/{appId}` |
| 参数 | `appId`：path，string，必填，应用 ID。<br />`version`：query，string，可选，包版本号，缺省按 `应用编码+时间戳` 自动生成。<br />`description`：query，string，可选，版本描述，缺省为"当前环境打包形成的应用包"。<br />`appointMetas`：body，可选，`Map<String,String>`，键为表名、值为该表的打包范围（解析见 `PackageUtils.pickMetaData`），缺省打包全部表数据。 |
| 返回 | `ApiResult<AppVersion>`：data 为本次打包生成的应用版本记录（草稿状态，包文件写入 `.gdp` 并压缩为 `.zgdp` 登记为附件）；v2 引擎时走 `packetV2`；找不到可打包的应用等业务错误抛 `PackException`。 |

### packetMergeApp 合并打包

| 项 | 内容 |
| --- | --- |
| URL | `GET\|POST /package/packet/merge` |
| 参数 | `appId`：query，string，目标应用 ID。<br />`version`：query，string，可选，包版本号，缺省自动生成。<br />`description`：query，string，可选，版本描述，缺省为"对比合并产生的包"。<br />`appointMetas`：body，可选，`Map<String,Map<String,String>>`，键为待合并包的版本号（`AppVersion.version`）、值为表名→打包范围映射。 |
| 返回 | `ApiResult<AppVersion>`：data 为合并打包生成的应用版本记录；v2 引擎时走 `packetMergeV2`。 |

### downloadPackage 下载版本包

| 项 | 内容 |
| --- | --- |
| URL | `GET /package/downloadPackage/{versionId}` |
| 参数 | `versionId`：path，string，版本 ID。 |
| 返回 | 直接写响应流（无 JSON body）：`application/octet-stream`，`Content-Disposition: attachment`，输出版本包文件。 |

### uploadPackage 上传版本包

| 项 | 内容 |
| --- | --- |
| URL | `POST /package/uploadPackage/{appId}` |
| 参数 | `file`：form-data，文件，必填，上传的包文件（保存到配置的上传目录）。<br />`appId`：path，string，目标应用 ID。 |
| 返回 | `ApiResult<AppVersion>`：data 为登记生成的应用版本记录（记录上传路径与打包时间）。 |

### deployPackage 部署版本包

| 项 | 内容 |
| --- | --- |
| URL | `GET /package/deploy/{versionId}` |
| 参数 | `versionId`：path，string，版本 ID。 |
| 返回 | `ApiResult`：data 为空，`msg` 为"应用部署成功！"；v1 引擎在事务中清空并重写应用数据后刷新应用元数据缓存，v2 引擎走 `deployV2`（先备份当前版本，声明式事务部署）；`env=init_source` 的环境禁止部署；包校验不通过、元数据缺失、部署失败等抛 `PackException`（60xxx 错误码）。 |

### rollback 回滚备份版本

| 项 | 内容 |
| --- | --- |
| URL | `GET /package/rollback/{appId}` |
| 参数 | `appId`：path，string，应用 ID。 |
| 返回 | `ApiResult<?>`：以最近一次备份版本执行部署实现回滚（调用 `rollbackV2`，仅 v2 引擎），返回部署结果；无备份版本时抛 `PackException`。 |
